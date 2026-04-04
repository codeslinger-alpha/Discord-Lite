package com.discord.discord_lite.server;

import com.discord.discord_lite.model.Channel;
import com.discord.discord_lite.model.ChannelGroup;
import com.discord.discord_lite.model.ChatMessage;
import com.discord.discord_lite.model.DirectMessage;
import com.discord.discord_lite.model.MessageAttachment;
import com.discord.discord_lite.model.MessageReaction;
import com.discord.discord_lite.model.MessageReply;
import com.discord.discord_lite.model.PresenceUpdate;
import com.discord.discord_lite.model.Role;
import com.discord.discord_lite.model.UnreadStateSnapshot;
import com.discord.discord_lite.model.UserAccount;
import com.discord.discord_lite.model.UserProfileDetails;
import com.discord.discord_lite.model.UserStatus;
import com.discord.discord_lite.model.UserSummary;
import com.discord.discord_lite.model.WorkspaceServer;
import com.discord.discord_lite.persistence.FileStorage;
import com.discord.discord_lite.security.PasswordHasher;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class LanServerState {
    private static final int MAX_ATTACHMENTS_PER_MESSAGE = 10;
    private static final long MAX_ATTACHMENT_BYTES = 8_000_000;
    private static final long MAX_TOTAL_ATTACHMENT_BYTES = 24_000_000;

    private final FileStorage storage;
    private final Map<String, UserAccount> usersById = new HashMap<>();
    private final Map<String, WorkspaceServer> serversById = new HashMap<>();
    private final Map<String, String> serverIdByInviteCode = new HashMap<>();
    private final Map<String, List<Channel>> channelsByServerId = new HashMap<>();
    private final Map<String, List<ChannelGroup>> channelGroupsByServerId = new HashMap<>();
    private final Map<String, List<ChatMessage>> channelMessagesByKey = new HashMap<>();
    private final Map<String, List<DirectMessage>> dmMessagesByKey = new HashMap<>();
    private final Map<String, Set<String>> dmPeersByUserId = new HashMap<>();

    public LanServerState(Path dataRoot) {
        this.storage = new FileStorage(dataRoot);
        boolean usersMigrated = false;
        Set<String> legacyReadStateUsers = new HashSet<>();
        for (UserAccount user : storage.loadUsers()) {
            if (user.getName() == null || user.getName().isBlank()) {
                user.setName(user.getUsername());
                usersMigrated = true;
            }
            if (user.getBlockedUserIds() == null) {
                user.setBlockedUserIds(new HashSet<>());
                usersMigrated = true;
            }
            if (user.getStatus() == null) {
                user.setStatus(UserStatus.ACTIVE);
                usersMigrated = true;
            }
            if (user.getLastReadChannelAtByKey().isEmpty() && user.getLastReadDmAtByPeerUserId().isEmpty()) {
                legacyReadStateUsers.add(user.getId());
            }
            usersById.put(user.getId(), user);
        }
        for (WorkspaceServer server : storage.loadServers()) {
            if (server.getBannedUserIds() == null) {
                server.setBannedUserIds(new HashSet<>());
            }
            serversById.put(server.getId(), server);
            boolean inviteCodeChanged = ensureInviteCode(server);
            List<Channel> channels = new ArrayList<>(storage.loadChannels(server.getId()));
            List<ChannelGroup> groups = new ArrayList<>(storage.loadChannelGroups(server.getId()));
            groups.sort(Comparator.comparingInt(ChannelGroup::getSortOrder));
            channelGroupsByServerId.put(server.getId(), groups);

            ChannelGroup defaultGroup = ensureDefaultGroup(server.getId());
            boolean migrated = false;
            for (Channel channel : channels) {
                if (channel.getGroupId() == null || channel.getGroupId().isBlank()) {
                    channel.setGroupId(defaultGroup.getId());
                    storage.saveChannel(channel);
                    migrated = true;
                }
            }

            channels.sort(channelComparator(groups));
            channelsByServerId.put(server.getId(), channels);
            if (migrated || inviteCodeChanged) {
                storage.saveServer(server);
            }
            if (migrated) {
                channelGroupsByServerId.put(server.getId(), groupsForServer(server.getId()));
            }
        }
        for (String[] pair : storage.listDirectMessagePairs()) {
            registerPeerPair(pair[0], pair[1]);
        }
        if (!legacyReadStateUsers.isEmpty()) {
            initializeLegacyReadMarkers(legacyReadStateUsers);
            usersMigrated = true;
        }
        if (usersMigrated) {
            persistUsers();
        }
    }

    public synchronized UserAccount register(String name, String username, String password) {
        String normalizedName = normalizeNonEmpty(name, "Name");
        String normalizedUsername = normalizeUsername(normalizeNonEmpty(username, "Username"));
        validatePassword(password);

        boolean exists = usersById.values().stream()
            .anyMatch(user -> user.getUsername().equalsIgnoreCase(normalizedUsername));
        if (exists) {
            throw new IllegalArgumentException("Username already exists");
        }

        String salt = PasswordHasher.createSalt();
        String hash = PasswordHasher.hashPassword(password, salt);
        UserAccount user = UserAccount.create(normalizedName, normalizedUsername, hash, salt);
        usersById.put(user.getId(), user);
        persistUsers();
        return user;
    }

    public synchronized UserAccount login(String username, String password) {
        String normalizedUsername = normalizeUsername(normalizeNonEmpty(username, "Username"));

        UserAccount user = usersById.values().stream()
            .filter(candidate -> candidate.getUsername().equalsIgnoreCase(normalizedUsername))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean valid = PasswordHasher.verifyPassword(password, user.getSalt(), user.getPasswordHash());
        if (!valid) {
            throw new IllegalArgumentException("Incorrect password");
        }
        return user;
    }

    public synchronized Optional<UserAccount> userById(String userId) {
        return Optional.ofNullable(usersById.get(userId));
    }

    public synchronized UserSummary summaryForUser(String userId, String requesterUserId, Set<String> onlineUserIds) {
        UserAccount user = usersById.get(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return toUserSummary(user, requesterUserId, onlineUserIds.contains(userId));
    }

    public synchronized UserProfileDetails profileForUser(String targetUserId, String requesterUserId, Set<String> onlineUserIds) {
        UserAccount user = usersById.get(targetUserId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        requireUser(requesterUserId);
        return toUserProfileDetails(user, requesterUserId, onlineUserIds.contains(targetUserId));
    }

    public synchronized PresenceUpdate presenceForUser(String userId, Set<String> onlineUserIds) {
        UserAccount user = usersById.get(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        boolean online = onlineUserIds.contains(userId) && user.getStatus() != UserStatus.INVISIBLE;
        return new PresenceUpdate(
            user.getId(),
            user.getName(),
            user.getUsername(),
            online,
            user.getStatus(),
            user.getProfileImageBase64()
        );
    }

    public synchronized List<UserSummary> listUsers(String requesterUserId, Set<String> onlineUserIds) {
        return usersById.values().stream()
            .filter(user -> !user.getId().equals(requesterUserId))
            .map(user -> toUserSummary(user, requesterUserId, onlineUserIds.contains(user.getId())))
            .sorted(Comparator.comparing(UserSummary::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public synchronized List<WorkspaceServer> listServersForUser(String userId) {
        return serversById.values().stream()
            .filter(server -> server.getMemberUserIds().contains(userId))
            .sorted(Comparator.comparing(WorkspaceServer::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public synchronized WorkspaceServer createServer(String serverName, String ownerUserId) {
        String normalizedName = normalizeNonEmpty(serverName, "Server name");
        requireUser(ownerUserId);

        WorkspaceServer server = WorkspaceServer.create(normalizedName, ownerUserId);
        server.setInviteCode(nextUniqueInviteCode());
        serversById.put(server.getId(), server);
        serverIdByInviteCode.put(server.getInviteCode(), server.getId());
        storage.saveServer(server);

        ChannelGroup defaultGroup = ensureDefaultGroup(server.getId());
        Channel general = Channel.create(server.getId(), defaultGroup.getId(), "general");
        channelsByServerId.computeIfAbsent(server.getId(), ignored -> new ArrayList<>()).add(general);
        storage.saveChannel(general);
        return server;
    }

    public synchronized void joinServer(String serverIdOrInviteCode, String userId) {
        WorkspaceServer server = requireServerMemberEligible(resolveServerIdOrInviteCode(serverIdOrInviteCode));
        requireUser(userId);
        if (server.getBannedUserIds().contains(userId)) {
            throw new IllegalStateException("You are banned from this server");
        }
        if (!server.getMemberUserIds().contains(userId)) {
            server.getMemberUserIds().add(userId);
            server.getRolesByUserId().putIfAbsent(userId, Role.MEMBER);
            markAllServerChannelsRead(userId, server.getId());
            storage.saveServer(server);
        }
    }

    public synchronized List<Channel> listChannels(String serverId, String userId) {
        WorkspaceServer server = requireMember(serverId, userId);
        List<ChannelGroup> groups = groupsForServer(server.getId());
        return channelsByServerId.getOrDefault(server.getId(), List.of()).stream()
            .sorted(channelComparator(groups))
            .toList();
    }

    public synchronized List<ChannelGroup> listChannelGroups(String serverId, String userId) {
        WorkspaceServer server = requireMember(serverId, userId);
        ensureDefaultGroup(server.getId());
        return new ArrayList<>(groupsForServer(server.getId()));
    }

    public synchronized List<UserSummary> listServerMembers(
        String serverId,
        String requesterUserId,
        Set<String> onlineUserIds
    ) {
        WorkspaceServer server = requireMember(serverId, requesterUserId);
        return server.getMemberUserIds().stream()
            .map(usersById::get)
            .filter(user -> user != null)
            .map(user -> toUserSummary(user, requesterUserId, onlineUserIds.contains(user.getId())))
            .sorted(
                Comparator.comparing(UserSummary::isOnline)
                    .reversed()
                    .thenComparing(UserSummary::displayName, String.CASE_INSENSITIVE_ORDER)
            )
            .toList();
    }

    public synchronized Channel createChannel(String serverId, String userId, String channelName) {
        return createChannel(serverId, userId, channelName, null);
    }

    public synchronized Channel createChannel(String serverId, String userId, String channelName, String groupId) {
        WorkspaceServer server = requireMember(serverId, userId);
        Role role = server.roleOf(userId);
        if (!(role == Role.OWNER || role == Role.ADMIN)) {
            throw new IllegalStateException("Only owner/admin can create channels");
        }

        String normalizedName = normalizeNonEmpty(channelName, "Channel name");
        List<Channel> channels = channelsByServerId.computeIfAbsent(serverId, ignored -> new ArrayList<>());
        boolean exists = channels.stream().anyMatch(channel -> channel.getName().equalsIgnoreCase(normalizedName));
        if (exists) {
            throw new IllegalArgumentException("Channel already exists");
        }

        String resolvedGroupId = resolveGroupIdForChannel(serverId, groupId);
        Channel channel = Channel.create(serverId, resolvedGroupId, normalizedName);
        channels.add(channel);
        channels.sort(channelComparator(groupsForServer(serverId)));
        storage.saveChannel(channel);
        return channel;
    }

    public synchronized ChannelGroup createChannelGroup(String serverId, String userId, String groupName) {
        WorkspaceServer server = requireMember(serverId, userId);
        Role role = server.roleOf(userId);
        if (!(role == Role.OWNER || role == Role.ADMIN)) {
            throw new IllegalStateException("Only owner/admin can create channel groups");
        }

        String normalizedName = normalizeNonEmpty(groupName, "Group name");
        ensureDefaultGroup(serverId);
        List<ChannelGroup> groups = groupsForServer(serverId);
        boolean exists = groups.stream().anyMatch(group -> group.getName().equalsIgnoreCase(normalizedName));
        if (exists) {
            throw new IllegalArgumentException("Channel group already exists");
        }

        int nextSortOrder = groups.stream().mapToInt(ChannelGroup::getSortOrder).max().orElse(0) + 1;
        ChannelGroup group = ChannelGroup.create(serverId, normalizedName, nextSortOrder);
        groups.add(group);
        groups.sort(Comparator.comparingInt(ChannelGroup::getSortOrder));
        storage.saveChannelGroup(group);
        return group;
    }

    public synchronized List<UserSummary> listChannelMembers(
        String serverId,
        String channelId,
        String requesterUserId,
        Set<String> onlineUserIds
    ) {
        requireChannelMembership(serverId, channelId, requesterUserId);
        return listServerMembers(serverId, requesterUserId, onlineUserIds);
    }

    public synchronized List<ChatMessage> listChannelMessages(String serverId, String channelId, String userId) {
        requireChannelMembership(serverId, channelId, userId);
        String key = channelKey(serverId, channelId);
        return new ArrayList<>(channelMessagesByKey.computeIfAbsent(key, ignored -> {
            List<ChatMessage> loaded = storage.loadMessages(serverId, channelId);
            loaded.sort(Comparator.comparing(ChatMessage::getCreatedAt));
            return loaded;
        }));
    }

    public synchronized ChatMessage sendChannelMessage(
        String serverId,
        String channelId,
        UserAccount sender,
        String content,
        String replyToMessageId,
        List<MessageAttachment> attachments
    ) {
        requireChannelMembership(serverId, channelId, sender.getId());
        String normalizedContent = normalizeMessageContent(content);
        List<MessageAttachment> normalizedAttachments = normalizeAttachments(attachments);
        validateMessagePayload(normalizedContent, normalizedAttachments);
        String key = channelKey(serverId, channelId);
        List<ChatMessage> messages = channelMessages(serverId, channelId);
        MessageReply replyTo = createChannelReply(serverId, channelId, replyToMessageId, messages);
        ChatMessage message = ChatMessage.create(
            serverId,
            channelId,
            sender.getId(),
            sender.getName(),
            normalizedContent,
            replyTo,
            normalizedAttachments
        );
        messages.add(message);
        storage.appendMessage(message);
        sender.getLastReadChannelAtByKey().put(key, message.getCreatedAt());
        persistUsers();
        return message;
    }

    public synchronized ChatMessage editChannelMessage(
        String serverId,
        String channelId,
        String messageId,
        String userId,
        String content
    ) {
        requireChannelMembership(serverId, channelId, userId);
        List<ChatMessage> messages = channelMessages(serverId, channelId);
        ChatMessage message = requireChannelMessage(messages, messageId);
        if (!userId.equals(message.getSenderUserId())) {
            throw new IllegalStateException("You can only edit your own messages");
        }

        String normalizedContent = normalizeMessageContent(content);
        validateMessagePayload(normalizedContent, message.getAttachments());
        if (normalizedContent.equals(message.getContent())) {
            return message;
        }

        message.setContent(normalizedContent);
        message.setEditedAt(Instant.now());
        updateChannelReplySnapshots(messages, message.getId(), normalizedContent);
        storage.saveMessages(serverId, channelId, messages);
        return message;
    }

    public synchronized void deleteChannelMessage(String serverId, String channelId, String messageId, String userId) {
        requireChannelMembership(serverId, channelId, userId);
        List<ChatMessage> messages = channelMessages(serverId, channelId);
        ChatMessage message = requireChannelMessage(messages, messageId);
        if (!userId.equals(message.getSenderUserId())) {
            throw new IllegalStateException("You can only delete your own messages");
        }

        messages.remove(message);
        updateChannelReplySnapshots(messages, message.getId(), "Original message was deleted");
        storage.saveMessages(serverId, channelId, messages);
    }

    public synchronized ChatMessage toggleChannelReaction(
        String serverId,
        String channelId,
        String messageId,
        String userId,
        String emoji
    ) {
        requireChannelMembership(serverId, channelId, userId);
        String key = channelKey(serverId, channelId);
        List<ChatMessage> messages = channelMessagesByKey.computeIfAbsent(key, ignored -> {
            List<ChatMessage> loaded = storage.loadMessages(serverId, channelId);
            loaded.sort(Comparator.comparing(ChatMessage::getCreatedAt));
            return loaded;
        });
        ChatMessage message = messages.stream()
            .filter(candidate -> candidate.getId().equals(normalizeNonEmpty(messageId, "Message ID")))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        toggleReaction(message.getReactions(), normalizeReactionEmoji(emoji), userId);
        storage.saveMessages(serverId, channelId, messages);
        return message;
    }

    public synchronized void openDirectMessage(String userId, String targetUserId) {
        requireUser(userId);
        requireUser(targetUserId);
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot open a DM with yourself");
        }
        if (isBlockedEitherWay(userId, targetUserId)) {
            throw new IllegalStateException("This DM is blocked");
        }
        registerPeerPair(userId, targetUserId);
    }

    public synchronized List<UserSummary> listDmPeers(String userId, Set<String> onlineUserIds) {
        requireUser(userId);
        return dmPeersByUserId.getOrDefault(userId, Set.of()).stream()
            .map(usersById::get)
            .filter(user -> user != null)
            .map(user -> toUserSummary(user, userId, onlineUserIds.contains(user.getId())))
            .sorted(Comparator.comparing(UserSummary::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public synchronized List<DirectMessage> listDirectMessages(String userId, String targetUserId) {
        requireUser(userId);
        requireUser(targetUserId);
        String key = dmKey(userId, targetUserId);
        return new ArrayList<>(dmMessagesByKey.computeIfAbsent(key, ignored -> {
            List<DirectMessage> loaded = storage.loadDirectMessages(userId, targetUserId);
            loaded.sort(Comparator.comparing(DirectMessage::getCreatedAt));
            return loaded;
        }));
    }

    public synchronized UnreadStateSnapshot unreadStateForUser(String userId) {
        UserAccount user = requireExistingUser(userId);

        Map<String, Integer> channelUnreadCounts = new HashMap<>();
        for (WorkspaceServer server : listServersForUser(userId)) {
            for (Channel channel : channelsByServerId.getOrDefault(server.getId(), List.of())) {
                int unread = unreadCountForChannel(user, channel.getServerId(), channel.getId());
                if (unread > 0) {
                    channelUnreadCounts.put(channelKey(channel.getServerId(), channel.getId()), unread);
                }
            }
        }

        Map<String, Integer> dmUnreadCounts = new HashMap<>();
        for (String peerUserId : dmPeersByUserId.getOrDefault(userId, Set.of())) {
            int unread = unreadCountForDm(user, peerUserId);
            if (unread > 0) {
                dmUnreadCounts.put(peerUserId, unread);
            }
        }

        return new UnreadStateSnapshot(channelUnreadCounts, dmUnreadCounts);
    }

    public synchronized void markChannelRead(String userId, String serverId, String channelId) {
        requireChannelMembership(serverId, channelId, userId);
        UserAccount user = requireExistingUser(userId);
        user.getLastReadChannelAtByKey().put(channelKey(serverId, channelId), latestChannelReadMarker(serverId, channelId));
        persistUsers();
    }

    public synchronized void markDmRead(String userId, String targetUserId) {
        requireUser(userId);
        requireUser(targetUserId);
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot mark a DM with yourself as read");
        }
        UserAccount user = requireExistingUser(userId);
        user.getLastReadDmAtByPeerUserId().put(targetUserId, latestDmReadMarker(userId, targetUserId));
        persistUsers();
    }

    public synchronized DirectMessage sendDirectMessage(
        UserAccount sender,
        String targetUserId,
        String content,
        String replyToMessageId,
        List<MessageAttachment> attachments
    ) {
        requireUser(targetUserId);
        if (sender.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot send a DM to yourself");
        }
        if (isBlockedEitherWay(sender.getId(), targetUserId)) {
            throw new IllegalStateException("This DM is blocked");
        }

        String normalizedContent = normalizeMessageContent(content);
        List<MessageAttachment> normalizedAttachments = normalizeAttachments(attachments);
        validateMessagePayload(normalizedContent, normalizedAttachments);
        String[] pair = sortedPair(sender.getId(), targetUserId);
        String key = dmKey(pair[0], pair[1]);
        List<DirectMessage> messages = directMessages(pair[0], pair[1]);
        MessageReply replyTo = createDirectReply(pair[0], pair[1], replyToMessageId, messages);
        DirectMessage message = DirectMessage.create(
            pair[0],
            pair[1],
            sender.getId(),
            sender.getName(),
            normalizedContent,
            replyTo,
            normalizedAttachments
        );
        messages.add(message);
        storage.appendDirectMessage(message);
        registerPeerPair(pair[0], pair[1]);
        sender.getLastReadDmAtByPeerUserId().put(targetUserId, message.getCreatedAt());
        persistUsers();
        return message;
    }

    public synchronized DirectMessage editDirectMessage(String userId, String targetUserId, String messageId, String content) {
        requireUser(userId);
        requireUser(targetUserId);
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot edit messages in a DM with yourself");
        }

        String[] pair = sortedPair(userId, targetUserId);
        List<DirectMessage> messages = directMessages(pair[0], pair[1]);
        DirectMessage message = requireDirectMessage(messages, messageId);
        if (!userId.equals(message.getSenderUserId())) {
            throw new IllegalStateException("You can only edit your own direct messages");
        }

        String normalizedContent = normalizeMessageContent(content);
        validateMessagePayload(normalizedContent, message.getAttachments());
        if (normalizedContent.equals(message.getContent())) {
            return message;
        }

        message.setContent(normalizedContent);
        message.setEditedAt(Instant.now());
        updateDirectReplySnapshots(messages, message.getId(), normalizedContent);
        storage.saveDirectMessages(pair[0], pair[1], messages);
        return message;
    }

    public synchronized DirectMessage toggleDirectReaction(
        String userId,
        String targetUserId,
        String messageId,
        String emoji
    ) {
        requireUser(userId);
        requireUser(targetUserId);
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot react in a DM with yourself");
        }
        String[] pair = sortedPair(userId, targetUserId);
        String key = dmKey(pair[0], pair[1]);
        List<DirectMessage> messages = dmMessagesByKey.computeIfAbsent(key, ignored -> {
            List<DirectMessage> loaded = storage.loadDirectMessages(pair[0], pair[1]);
            loaded.sort(Comparator.comparing(DirectMessage::getCreatedAt));
            return loaded;
        });
        DirectMessage message = messages.stream()
            .filter(candidate -> candidate.getId().equals(normalizeNonEmpty(messageId, "Message ID")))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        toggleReaction(message.getReactions(), normalizeReactionEmoji(emoji), userId);
        storage.saveDirectMessages(pair[0], pair[1], messages);
        return message;
    }

    public synchronized void deleteDirectMessage(String userId, String targetUserId, String messageId) {
        requireUser(userId);
        requireUser(targetUserId);
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot delete messages in a DM with yourself");
        }

        String[] pair = sortedPair(userId, targetUserId);
        String key = dmKey(pair[0], pair[1]);
        List<DirectMessage> messages = dmMessagesByKey.computeIfAbsent(key, ignored -> {
            List<DirectMessage> loaded = storage.loadDirectMessages(pair[0], pair[1]);
            loaded.sort(Comparator.comparing(DirectMessage::getCreatedAt));
            return loaded;
        });

        DirectMessage message = messages.stream()
            .filter(candidate -> candidate.getId().equals(normalizeNonEmpty(messageId, "Message ID")))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (!userId.equals(message.getSenderUserId())) {
            throw new IllegalStateException("You can only delete your own direct messages");
        }

        messages.remove(message);
        storage.saveDirectMessages(pair[0], pair[1], messages);
    }

    public synchronized void deleteDirectConversation(String userId, String targetUserId) {
        requireUser(userId);
        requireUser(targetUserId);
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot delete a DM with yourself");
        }

        String[] pair = sortedPair(userId, targetUserId);
        dmMessagesByKey.remove(dmKey(pair[0], pair[1]));
        unregisterPeerPair(pair[0], pair[1]);
        UserAccount firstUser = requireExistingUser(pair[0]);
        UserAccount secondUser = requireExistingUser(pair[1]);
        firstUser.getLastReadDmAtByPeerUserId().remove(pair[1]);
        secondUser.getLastReadDmAtByPeerUserId().remove(pair[0]);
        persistUsers();
        storage.deleteDirectConversation(pair[0], pair[1]);
    }

    private void initializeLegacyReadMarkers(Set<String> userIds) {
        for (String userId : userIds) {
            UserAccount user = usersById.get(userId);
            if (user == null) {
                continue;
            }
            for (WorkspaceServer server : listServersForUser(userId)) {
                for (Channel channel : channelsByServerId.getOrDefault(server.getId(), List.of())) {
                    user.getLastReadChannelAtByKey().put(
                        channelKey(channel.getServerId(), channel.getId()),
                        latestChannelReadMarker(channel.getServerId(), channel.getId())
                    );
                }
            }
            for (String peerUserId : dmPeersByUserId.getOrDefault(userId, Set.of())) {
                user.getLastReadDmAtByPeerUserId().put(peerUserId, latestDmReadMarker(userId, peerUserId));
            }
        }
    }

    private void markAllServerChannelsRead(String userId, String serverId) {
        UserAccount user = requireExistingUser(userId);
        requireMember(serverId, userId);
        for (Channel channel : channelsByServerId.getOrDefault(serverId, List.of())) {
            user.getLastReadChannelAtByKey().put(
                channelKey(channel.getServerId(), channel.getId()),
                latestChannelReadMarker(channel.getServerId(), channel.getId())
            );
        }
        persistUsers();
    }

    private int unreadCountForChannel(UserAccount user, String serverId, String channelId) {
        Instant lastReadAt = user.getLastReadChannelAtByKey().get(channelKey(serverId, channelId));
        return (int) channelMessages(serverId, channelId).stream()
            .filter(message -> !user.getId().equals(message.getSenderUserId()))
            .filter(message -> lastReadAt == null || message.getCreatedAt().isAfter(lastReadAt))
            .count();
    }

    private int unreadCountForDm(UserAccount user, String targetUserId) {
        Instant lastReadAt = user.getLastReadDmAtByPeerUserId().get(targetUserId);
        String[] pair = sortedPair(user.getId(), targetUserId);
        return (int) directMessages(pair[0], pair[1]).stream()
            .filter(message -> !user.getId().equals(message.getSenderUserId()))
            .filter(message -> lastReadAt == null || message.getCreatedAt().isAfter(lastReadAt))
            .count();
    }

    private Instant latestChannelReadMarker(String serverId, String channelId) {
        List<ChatMessage> messages = channelMessages(serverId, channelId);
        if (messages.isEmpty()) {
            return Instant.now();
        }
        return messages.get(messages.size() - 1).getCreatedAt();
    }

    private Instant latestDmReadMarker(String userId, String targetUserId) {
        String[] pair = sortedPair(userId, targetUserId);
        List<DirectMessage> messages = directMessages(pair[0], pair[1]);
        if (messages.isEmpty()) {
            return Instant.now();
        }
        return messages.get(messages.size() - 1).getCreatedAt();
    }

    private List<ChatMessage> channelMessages(String serverId, String channelId) {
        String key = channelKey(serverId, channelId);
        return channelMessagesByKey.computeIfAbsent(key, ignored -> {
            List<ChatMessage> loaded = storage.loadMessages(serverId, channelId);
            loaded.sort(Comparator.comparing(ChatMessage::getCreatedAt));
            return loaded;
        });
    }

    private List<DirectMessage> directMessages(String userIdA, String userIdB) {
        String key = dmKey(userIdA, userIdB);
        return dmMessagesByKey.computeIfAbsent(key, ignored -> {
            List<DirectMessage> loaded = storage.loadDirectMessages(userIdA, userIdB);
            loaded.sort(Comparator.comparing(DirectMessage::getCreatedAt));
            return loaded;
        });
    }

    private MessageReply createChannelReply(
        String serverId,
        String channelId,
        String replyToMessageId,
        List<ChatMessage> messages
    ) {
        if (replyToMessageId == null || replyToMessageId.isBlank()) {
            return null;
        }
        String normalizedId = normalizeNonEmpty(replyToMessageId, "Reply message ID");
        ChatMessage original = messages.stream()
            .filter(candidate -> normalizedId.equals(candidate.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Original message not found"));
        return new MessageReply(
            original.getId(),
            original.getSenderUserId(),
            original.getSenderUsername(),
            original.getContent(),
            original.getAttachments().size(),
            hasImageAttachment(original.getAttachments())
        );
    }

    private MessageReply createDirectReply(
        String userIdA,
        String userIdB,
        String replyToMessageId,
        List<DirectMessage> messages
    ) {
        if (replyToMessageId == null || replyToMessageId.isBlank()) {
            return null;
        }
        String normalizedId = normalizeNonEmpty(replyToMessageId, "Reply message ID");
        DirectMessage original = messages.stream()
            .filter(candidate -> normalizedId.equals(candidate.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Original message not found"));
        return new MessageReply(
            original.getId(),
            original.getSenderUserId(),
            original.getSenderUsername(),
            original.getContent(),
            original.getAttachments().size(),
            hasImageAttachment(original.getAttachments())
        );
    }

    private ChatMessage requireChannelMessage(List<ChatMessage> messages, String messageId) {
        String normalizedId = normalizeNonEmpty(messageId, "Message ID");
        return messages.stream()
            .filter(candidate -> normalizedId.equals(candidate.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Message not found"));
    }

    private DirectMessage requireDirectMessage(List<DirectMessage> messages, String messageId) {
        String normalizedId = normalizeNonEmpty(messageId, "Message ID");
        return messages.stream()
            .filter(candidate -> normalizedId.equals(candidate.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Message not found"));
    }

    private void updateChannelReplySnapshots(List<ChatMessage> messages, String originalMessageId, String nextContent) {
        for (ChatMessage candidate : messages) {
            updateReplySnapshot(candidate.getReplyTo(), originalMessageId, nextContent);
        }
    }

    private void updateDirectReplySnapshots(List<DirectMessage> messages, String originalMessageId, String nextContent) {
        for (DirectMessage candidate : messages) {
            updateReplySnapshot(candidate.getReplyTo(), originalMessageId, nextContent);
        }
    }

    private void updateReplySnapshot(MessageReply replyTo, String originalMessageId, String nextContent) {
        if (replyTo == null || replyTo.getMessageId() == null) {
            return;
        }
        if (replyTo.getMessageId().equals(originalMessageId)) {
            replyTo.setContent(nextContent);
        }
    }

    public synchronized List<String> suggestUsernames(String rawBase) {
        String base = sanitizeUsername(rawBase);
        if (base.isBlank()) {
            base = "user";
        }

        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        if (isUsernameAvailable(base)) {
            suggestions.add(base);
        }

        int counter = 1;
        while (suggestions.size() < 5) {
            String candidate = counter <= 3
                ? base + counter
                : base + "_" + String.format(Locale.ROOT, "%02d", counter);
            if (isUsernameAvailable(candidate)) {
                suggestions.add(candidate);
            }
            counter++;
        }
        return new ArrayList<>(suggestions);
    }

    public synchronized UserAccount updateProfile(
        String userId,
        String name,
        String currentPassword,
        String newPassword,
        UserStatus status,
        String profileImageBase64
    ) {
        UserAccount user = usersById.get(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        user.setName(normalizeNonEmpty(name, "Name"));
        user.setStatus(status == null ? UserStatus.ACTIVE : status);
        user.setProfileImageBase64(normalizeProfileImage(profileImageBase64));

        if (newPassword != null && !newPassword.isBlank()) {
            if (currentPassword == null || currentPassword.isBlank()) {
                throw new IllegalArgumentException("Current password is required");
            }
            boolean valid = PasswordHasher.verifyPassword(currentPassword, user.getSalt(), user.getPasswordHash());
            if (!valid) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            validatePassword(newPassword);
            String nextSalt = PasswordHasher.createSalt();
            user.setSalt(nextSalt);
            user.setPasswordHash(PasswordHasher.hashPassword(newPassword, nextSalt));
        }

        persistUsers();
        return user;
    }

    public synchronized List<UserSummary> listBannedUsers(String serverId, String requesterUserId, Set<String> onlineUserIds) {
        WorkspaceServer server = requireMember(serverId, requesterUserId);
        Role role = server.roleOf(requesterUserId);
        if (!(role == Role.OWNER || role == Role.ADMIN)) {
            throw new IllegalStateException("Only owner/admin can view bans");
        }
        return server.getBannedUserIds().stream()
            .map(usersById::get)
            .filter(user -> user != null)
            .map(user -> toUserSummary(user, requesterUserId, onlineUserIds.contains(user.getId())))
            .sorted(Comparator.comparing(UserSummary::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public synchronized void kickMember(String serverId, String actorUserId, String targetUserId) {
        WorkspaceServer server = requireMember(serverId, actorUserId);
        UserAccount target = requireExistingUser(targetUserId);
        ensureModerationAllowed(server, actorUserId, target.getId());
        removeMember(server, target.getId());
        storage.saveServer(server);
    }

    public synchronized void banMember(String serverId, String actorUserId, String targetUserId) {
        WorkspaceServer server = requireMember(serverId, actorUserId);
        UserAccount target = requireExistingUser(targetUserId);
        ensureModerationAllowed(server, actorUserId, target.getId());
        removeMember(server, target.getId());
        server.getBannedUserIds().add(target.getId());
        storage.saveServer(server);
    }

    public synchronized void unbanMember(String serverId, String actorUserId, String targetUserId) {
        WorkspaceServer server = requireMember(serverId, actorUserId);
        ensureServerManagement(server, actorUserId);
        if (!server.getBannedUserIds().remove(normalizeNonEmpty(targetUserId, "Target user ID"))) {
            throw new IllegalArgumentException("User is not banned");
        }
        storage.saveServer(server);
    }

    public synchronized void leaveServer(String serverId, String userId) {
        WorkspaceServer server = requireMember(serverId, userId);
        if (Role.OWNER == server.roleOf(userId)) {
            throw new IllegalStateException("Owner cannot leave the server. Delete it instead.");
        }
        removeMember(server, userId);
        storage.saveServer(server);
    }

    public synchronized Set<String> deleteServer(String serverId, String actorUserId) {
        WorkspaceServer server = requireMember(serverId, actorUserId);
        ensureServerManagement(server, actorUserId);
        Set<String> affectedUsers = new HashSet<>(server.getMemberUserIds());
        if (server.getInviteCode() != null) {
            serverIdByInviteCode.remove(server.getInviteCode());
        }
        String channelKeyPrefix = serverId + ":";
        usersById.values().forEach(user -> user.getLastReadChannelAtByKey().keySet().removeIf(key -> key.startsWith(channelKeyPrefix)));
        serversById.remove(serverId);
        channelsByServerId.remove(serverId);
        channelGroupsByServerId.remove(serverId);
        channelMessagesByKey.keySet().removeIf(key -> key.startsWith(serverId + ":"));
        persistUsers();
        storage.deleteServer(serverId);
        return affectedUsers;
    }

    public synchronized UserAccount blockUser(String userId, String targetUserId) {
        UserAccount user = requireExistingUser(userId);
        UserAccount target = requireExistingUser(targetUserId);
        if (user.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Cannot block yourself");
        }
        user.getBlockedUserIds().add(target.getId());
        persistUsers();
        return user;
    }

    public synchronized UserAccount unblockUser(String userId, String targetUserId) {
        UserAccount user = requireExistingUser(userId);
        UserAccount target = requireExistingUser(targetUserId);
        if (!user.getBlockedUserIds().remove(target.getId())) {
            throw new IllegalArgumentException("User is not blocked");
        }
        persistUsers();
        return user;
    }

    public synchronized void deleteAccount(String userId, String currentPassword) {
        UserAccount user = requireExistingUser(userId);
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("Current password is required");
        }
        boolean valid = PasswordHasher.verifyPassword(currentPassword, user.getSalt(), user.getPasswordHash());
        if (!valid) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        Set<String> ownedServerIds = serversById.values().stream()
            .filter(server -> userId.equals(server.getOwnerUserId()))
            .map(WorkspaceServer::getId)
            .collect(HashSet::new, HashSet::add, HashSet::addAll);
        ownedServerIds.forEach(serverId -> deleteServer(serverId, userId));

        for (WorkspaceServer server : serversById.values()) {
            boolean changed = server.getMemberUserIds().remove(userId);
            changed |= server.getBannedUserIds().remove(userId);
            changed |= server.getRolesByUserId().remove(userId) != null;
            if (changed) {
                storage.saveServer(server);
            }
        }

        for (UserAccount account : usersById.values()) {
            if (account.getBlockedUserIds() != null && account.getBlockedUserIds().remove(userId)) {
                persistUsers();
            }
            account.getLastReadDmAtByPeerUserId().remove(userId);
        }

        usersById.remove(userId);
        persistUsers();
        dmPeersByUserId.remove(userId);
        dmPeersByUserId.values().forEach(peers -> peers.remove(userId));
    }

    public synchronized WorkspaceServer updateServerAppearance(
        String serverId,
        String userId,
        String iconImageBase64,
        String coverImageBase64
    ) {
        WorkspaceServer server = requireMember(serverId, userId);
        Role role = server.roleOf(userId);
        if (!(role == Role.OWNER || role == Role.ADMIN)) {
            throw new IllegalStateException("Only owner/admin can edit server appearance");
        }

        server.setIconImageBase64(normalizeServerIcon(iconImageBase64));
        server.setCoverImageBase64(normalizeServerCover(coverImageBase64));
        storage.saveServer(server);
        return server;
    }

    public synchronized Set<String> memberIds(String serverId) {
        WorkspaceServer server = requireServerMemberEligible(serverId);
        return new HashSet<>(server.getMemberUserIds());
    }

    private List<ChannelGroup> groupsForServer(String serverId) {
        return channelGroupsByServerId.computeIfAbsent(serverId, ignored -> new ArrayList<>());
    }

    private ChannelGroup ensureDefaultGroup(String serverId) {
        List<ChannelGroup> groups = groupsForServer(serverId);
        if (groups.isEmpty()) {
            ChannelGroup group = ChannelGroup.create(serverId, "Text Channels", 1);
            groups.add(group);
            storage.saveChannelGroup(group);
            return group;
        }
        groups.sort(Comparator.comparingInt(ChannelGroup::getSortOrder));
        return groups.get(0);
    }

    private String resolveGroupIdForChannel(String serverId, String requestedGroupId) {
        ChannelGroup defaultGroup = ensureDefaultGroup(serverId);
        if (requestedGroupId == null || requestedGroupId.isBlank()) {
            return defaultGroup.getId();
        }

        String trimmed = requestedGroupId.trim();
        boolean exists = groupsForServer(serverId).stream().anyMatch(group -> group.getId().equals(trimmed));
        if (!exists) {
            throw new IllegalArgumentException("Channel group not found");
        }
        return trimmed;
    }

    private Comparator<Channel> channelComparator(List<ChannelGroup> groups) {
        Map<String, Integer> sortByGroupId = new HashMap<>();
        int fallbackOrder = Integer.MAX_VALUE / 2;
        for (ChannelGroup group : groups) {
            sortByGroupId.put(group.getId(), group.getSortOrder());
        }
        return Comparator
            .comparingInt((Channel channel) -> sortByGroupId.getOrDefault(channel.getGroupId(), fallbackOrder))
            .thenComparing(Channel::getName, String.CASE_INSENSITIVE_ORDER);
    }

    private WorkspaceServer requireServerMemberEligible(String serverId) {
        String normalizedId = normalizeNonEmpty(serverId, "Server ID");
        WorkspaceServer server = serversById.get(normalizedId);
        if (server == null) {
            throw new IllegalArgumentException("Server not found");
        }
        return server;
    }

    private String resolveServerIdOrInviteCode(String value) {
        String normalized = normalizeNonEmpty(value, "Server invite code or ID");
        WorkspaceServer byId = serversById.get(normalized);
        if (byId != null) {
            return byId.getId();
        }
        String byInviteCode = serverIdByInviteCode.get(normalized.toUpperCase());
        if (byInviteCode != null) {
            return byInviteCode;
        }
        throw new IllegalArgumentException("Server not found");
    }

    private boolean ensureInviteCode(WorkspaceServer server) {
        String normalized = normalizeInviteCode(server.getInviteCode());
        if (normalized == null || isInviteCodeTakenByAnotherServer(normalized, server.getId())) {
            String next = nextUniqueInviteCode();
            server.setInviteCode(next);
            serverIdByInviteCode.put(next, server.getId());
            return true;
        }
        server.setInviteCode(normalized);
        serverIdByInviteCode.put(normalized, server.getId());
        return false;
    }

    private boolean isInviteCodeTakenByAnotherServer(String inviteCode, String serverId) {
        String ownerServerId = serverIdByInviteCode.get(inviteCode);
        return ownerServerId != null && !ownerServerId.equals(serverId);
    }

    private String nextUniqueInviteCode() {
        String inviteCode;
        do {
            inviteCode = WorkspaceServer.generateInviteCode();
        } while (serverIdByInviteCode.containsKey(inviteCode));
        return inviteCode;
    }

    private String normalizeInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return null;
        }
        return inviteCode.trim().toUpperCase();
    }

    private WorkspaceServer requireMember(String serverId, String userId) {
        WorkspaceServer server = requireServerMemberEligible(serverId);
        if (!server.getMemberUserIds().contains(userId)) {
            throw new IllegalStateException("You are not a member of this server");
        }
        return server;
    }

    private void requireChannelMembership(String serverId, String channelId, String userId) {
        WorkspaceServer server = requireMember(serverId, userId);
        boolean channelExists = channelsByServerId.getOrDefault(server.getId(), List.of()).stream()
            .anyMatch(channel -> channel.getId().equals(channelId));
        if (!channelExists) {
            throw new IllegalArgumentException("Channel not found");
        }
    }

    private void requireUser(String userId) {
        if (userId == null || !usersById.containsKey(userId)) {
            throw new IllegalArgumentException("User not found");
        }
    }

    private UserAccount requireExistingUser(String userId) {
        String normalizedUserId = normalizeNonEmpty(userId, "User ID");
        UserAccount user = usersById.get(normalizedUserId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return user;
    }

    private boolean isBlockedEitherWay(String firstUserId, String secondUserId) {
        UserAccount first = requireExistingUser(firstUserId);
        UserAccount second = requireExistingUser(secondUserId);
        return first.getBlockedUserIds().contains(second.getId()) || second.getBlockedUserIds().contains(first.getId());
    }

    private void ensureServerManagement(WorkspaceServer server, String actorUserId) {
        Role actorRole = server.roleOf(actorUserId);
        if (!(actorRole == Role.OWNER || actorRole == Role.ADMIN)) {
            throw new IllegalStateException("Only owner/admin can manage this server");
        }
    }

    private void ensureModerationAllowed(WorkspaceServer server, String actorUserId, String targetUserId) {
        ensureServerManagement(server, actorUserId);
        if (actorUserId.equals(targetUserId)) {
            throw new IllegalStateException("You cannot moderate yourself");
        }
        if (!server.getMemberUserIds().contains(targetUserId)) {
            throw new IllegalArgumentException("Target user is not in this server");
        }

        Role actorRole = server.roleOf(actorUserId);
        Role targetRole = server.roleOf(targetUserId);
        if (targetRole == Role.OWNER) {
            throw new IllegalStateException("You cannot moderate the server owner");
        }
        if (actorRole != Role.OWNER && targetRole == Role.ADMIN) {
            throw new IllegalStateException("Admins cannot moderate other admins");
        }
    }

    private void removeMember(WorkspaceServer server, String userId) {
        server.getMemberUserIds().remove(userId);
        server.getRolesByUserId().remove(userId);
    }

    private UserSummary toUserSummary(UserAccount user, String requesterUserId, boolean connected) {
        boolean online = connected && (user.getStatus() != UserStatus.INVISIBLE || user.getId().equals(requesterUserId));
        return new UserSummary(
            user.getId(),
            user.getName(),
            user.getUsername(),
            online,
            user.getStatus(),
            user.getProfileImageBase64()
        );
    }

    private UserProfileDetails toUserProfileDetails(UserAccount user, String requesterUserId, boolean connected) {
        boolean online = connected && (user.getStatus() != UserStatus.INVISIBLE || user.getId().equals(requesterUserId));
        UserAccount requester = requireExistingUser(requesterUserId);
        List<String> mutualServerNames = serversById.values().stream()
            .filter(server ->
                server.getMemberUserIds().contains(requesterUserId) &&
                    server.getMemberUserIds().contains(user.getId())
            )
            .map(WorkspaceServer::getName)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        return new UserProfileDetails(
            user.getId(),
            user.getName(),
            user.getUsername(),
            online,
            user.getStatus(),
            user.getProfileImageBase64(),
            user.getCreatedAt(),
            requester.getBlockedUserIds().contains(user.getId()),
            user.getBlockedUserIds().contains(requesterUserId),
            mutualServerNames
        );
    }

    private void toggleReaction(List<MessageReaction> reactions, String emoji, String userId) {
        boolean removedSelectedReaction = false;
        for (int index = reactions.size() - 1; index >= 0; index--) {
            MessageReaction reaction = reactions.get(index);
            List<String> userIds = reaction.getUserIds();
            if (userIds.removeIf(userId::equals) && emoji.equals(reaction.getEmoji())) {
                removedSelectedReaction = true;
            }
            if (userIds.isEmpty()) {
                reactions.remove(index);
            }
        }

        if (removedSelectedReaction) {
            return;
        }

        MessageReaction existing = reactions.stream()
            .filter(reaction -> emoji.equals(reaction.getEmoji()))
            .findFirst()
            .orElse(null);
        if (existing == null) {
            reactions.add(new MessageReaction(emoji, List.of(userId)));
            return;
        }
        existing.getUserIds().add(userId);
    }

    private String normalizeReactionEmoji(String emoji) {
        String normalized = normalizeNonEmpty(emoji, "Emoji reaction");
        if (normalized.length() > 16) {
            throw new IllegalArgumentException("Reaction is too long");
        }
        return normalized;
    }

    private List<MessageAttachment> normalizeAttachments(List<MessageAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        if (attachments.size() > MAX_ATTACHMENTS_PER_MESSAGE) {
            throw new IllegalArgumentException("You can attach up to " + MAX_ATTACHMENTS_PER_MESSAGE + " files per message");
        }

        List<MessageAttachment> normalizedAttachments = new ArrayList<>();
        long totalBytes = 0;
        for (MessageAttachment attachment : attachments) {
            MessageAttachment normalized = normalizeAttachment(attachment);
            totalBytes += normalized.getSizeBytes();
            if (totalBytes > MAX_TOTAL_ATTACHMENT_BYTES) {
                throw new IllegalArgumentException("Attachments exceed the maximum total size");
            }
            normalizedAttachments.add(normalized);
        }
        return normalizedAttachments;
    }

    private MessageAttachment normalizeAttachment(MessageAttachment attachment) {
        if (attachment == null) {
            throw new IllegalArgumentException("Attachment is invalid");
        }

        String fileName = sanitizeAttachmentFileName(attachment.getFileName());
        String base64Content = normalizeNonEmpty(attachment.getBase64Content(), "Attachment data");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Content);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Attachment data is invalid");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Attachment is empty");
        }
        if (bytes.length > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException(fileName + " exceeds the maximum attachment size");
        }

        String contentType = normalizeAttachmentContentType(attachment.getContentType(), fileName);
        String id = attachment.getId() == null || attachment.getId().isBlank()
            ? UUID.randomUUID().toString()
            : attachment.getId().trim();
        return new MessageAttachment(id, fileName, contentType, bytes.length, base64Content);
    }

    private String sanitizeAttachmentFileName(String value) {
        String normalized = normalizeNonEmpty(value, "Attachment file name");
        normalized = normalized.replace('\\', '/');
        int separatorIndex = normalized.lastIndexOf('/');
        if (separatorIndex >= 0) {
            normalized = normalized.substring(separatorIndex + 1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Attachment file name is invalid");
        }
        return normalized;
    }

    private String normalizeAttachmentContentType(String contentType, String fileName) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType.trim().toLowerCase(Locale.ROOT);
        }
        String extension = "";
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex >= 0 && extensionIndex < fileName.length() - 1) {
            extension = fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        }
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "txt", "log", "md" -> "text/plain";
            case "pdf" -> "application/pdf";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    private boolean hasImageAttachment(List<MessageAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return false;
        }
        return attachments.stream().anyMatch(this::isImageAttachment);
    }

    private boolean isImageAttachment(MessageAttachment attachment) {
        if (attachment == null || attachment.getContentType() == null) {
            return false;
        }
        return attachment.getContentType().toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private String normalizeMessageContent(String content) {
        return content == null ? "" : content.trim();
    }

    private void validateMessagePayload(String content, List<MessageAttachment> attachments) {
        if ((content == null || content.isBlank()) && (attachments == null || attachments.isEmpty())) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
    }

    private String normalizeNonEmpty(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        return value.trim();
    }

    private String normalizeUsername(String username) {
        String normalized = sanitizeUsername(username);
        if (normalized.length() < 3 || normalized.length() > 20) {
            throw new IllegalArgumentException("Username must be 3-20 characters");
        }
        if (!normalized.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException("Username can only contain letters, numbers, ., _, and -");
        }
        return normalized;
    }

    private String sanitizeUsername(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9._-]+", "_");
        normalized = normalized.replaceAll("^[._-]+|[._-]+$", "");
        normalized = normalized.replaceAll("_{2,}", "_");
        return normalized;
    }

    private boolean isUsernameAvailable(String username) {
        return usersById.values().stream().noneMatch(user -> user.getUsername().equalsIgnoreCase(username));
    }

    private String normalizeProfileImage(String profileImageBase64) {
        return normalizeImageBase64(profileImageBase64, "Profile picture", 1_500_000);
    }

    private String normalizeServerIcon(String iconImageBase64) {
        return normalizeImageBase64(iconImageBase64, "Server icon", 1_500_000);
    }

    private String normalizeServerCover(String coverImageBase64) {
        return normalizeImageBase64(coverImageBase64, "Server cover", 3_500_000);
    }

    private String normalizeImageBase64(String imageBase64, String label, int maxBytes) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            return null;
        }
        String normalized = imageBase64.trim();
        try {
            byte[] bytes = Base64.getDecoder().decode(normalized);
            if (bytes.length > maxBytes) {
                throw new IllegalArgumentException(label + " must be smaller than " + (maxBytes / 1_000_000.0) + " MB");
            }
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains(label)) {
                throw ex;
            }
            throw new IllegalArgumentException(label + " is invalid");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
    }

    private void persistUsers() {
        List<UserAccount> users = new ArrayList<>(usersById.values());
        users.sort(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER));
        storage.saveUsers(users);
    }

    private String channelKey(String serverId, String channelId) {
        return serverId + ":" + channelId;
    }

    private String dmKey(String firstUserId, String secondUserId) {
        String[] sorted = sortedPair(firstUserId, secondUserId);
        return sorted[0] + ":" + sorted[1];
    }

    private String[] sortedPair(String first, String second) {
        if (first.compareTo(second) <= 0) {
            return new String[] {first, second};
        }
        return new String[] {second, first};
    }

    private void registerPeerPair(String userAId, String userBId) {
        if (userAId.equals(userBId)) {
            return;
        }
        dmPeersByUserId.computeIfAbsent(userAId, ignored -> new HashSet<>()).add(userBId);
        dmPeersByUserId.computeIfAbsent(userBId, ignored -> new HashSet<>()).add(userAId);
    }

    private void unregisterPeerPair(String userAId, String userBId) {
        Set<String> firstPeers = dmPeersByUserId.get(userAId);
        if (firstPeers != null) {
            firstPeers.remove(userBId);
            if (firstPeers.isEmpty()) {
                dmPeersByUserId.remove(userAId);
            }
        }
        Set<String> secondPeers = dmPeersByUserId.get(userBId);
        if (secondPeers != null) {
            secondPeers.remove(userAId);
            if (secondPeers.isEmpty()) {
                dmPeersByUserId.remove(userBId);
            }
        }
    }
}
