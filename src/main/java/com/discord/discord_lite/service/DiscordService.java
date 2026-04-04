package com.discord.discord_lite.service;

import com.discord.discord_lite.model.Channel;
import com.discord.discord_lite.model.ChatMessage;
import com.discord.discord_lite.model.Role;
import com.discord.discord_lite.model.UserAccount;
import com.discord.discord_lite.model.WorkspaceServer;
import com.discord.discord_lite.persistence.FileStorage;
import com.discord.discord_lite.security.PasswordHasher;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class DiscordService {
    private final FileStorage storage;
    private final List<Consumer<AppEvent>> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, WorkspaceServer> serversById = new HashMap<>();
    private final Map<String, String> serverIdByInviteCode = new HashMap<>();
    private final Map<String, List<Channel>> channelsByServerId = new HashMap<>();
    private final Map<String, List<ChatMessage>> messagesByChannelKey = new HashMap<>();
    private final Map<String, Integer> unreadByChannelKey = new HashMap<>();

    private List<UserAccount> users;
    private UserAccount currentUser;
    private String activeChannelKey;

    public DiscordService(Path dataRoot) {
        this.storage = new FileStorage(dataRoot);
        this.users = storage.loadUsers();
        loadServerData();
    }

    public synchronized Optional<UserAccount> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    public synchronized String currentUsername() {
        return currentUser == null ? "Guest" : currentUser.getUsername();
    }

    public synchronized void register(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        validatePassword(password);

        boolean exists = users.stream()
            .anyMatch(user -> user.getUsername().equalsIgnoreCase(normalizedUsername));
        if (exists) {
            throw new IllegalArgumentException("Username already exists");
        }

        String salt = PasswordHasher.createSalt();
        String hash = PasswordHasher.hashPassword(password, salt);
        users.add(UserAccount.create(normalizedUsername, normalizedUsername, hash, salt));
        users.sort(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER));
        storage.saveUsers(users);
    }

    public synchronized void login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);

        UserAccount user = users.stream()
            .filter(candidate -> candidate.getUsername().equalsIgnoreCase(normalizedUsername))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean valid = PasswordHasher.verifyPassword(password, user.getSalt(), user.getPasswordHash());
        if (!valid) {
            throw new IllegalArgumentException("Incorrect password");
        }

        currentUser = user;
        unreadByChannelKey.clear();
        activeChannelKey = null;
        notifyListeners(new AppEvent(EventType.SESSION_CHANGED, null, null, null));
    }

    public synchronized void logout() {
        currentUser = null;
        activeChannelKey = null;
        unreadByChannelKey.clear();
        notifyListeners(new AppEvent(EventType.SESSION_CHANGED, null, null, null));
    }

    public synchronized WorkspaceServer createServer(String serverName) {
        requireLoggedIn();
        String normalizedName = normalizeNonEmpty(serverName, "Server name");

        WorkspaceServer server = WorkspaceServer.create(normalizedName, currentUser.getId());
        server.setInviteCode(nextUniqueInviteCode());
        serversById.put(server.getId(), server);
        serverIdByInviteCode.put(server.getInviteCode(), server.getId());
        storage.saveServer(server);

        Channel general = Channel.create(server.getId(), null, "general");
        channelsByServerId.computeIfAbsent(server.getId(), ignored -> new ArrayList<>()).add(general);
        storage.saveChannel(general);

        notifyListeners(new AppEvent(EventType.DATA_CHANGED, server.getId(), null, null));
        return server;
    }

    public synchronized void joinServer(String serverIdOrInviteCode) {
        requireLoggedIn();
        String normalizedId = resolveServerIdOrInviteCode(serverIdOrInviteCode);
        WorkspaceServer server = serversById.get(normalizedId);
        if (server == null) {
            throw new IllegalArgumentException("Server not found");
        }

        if (!server.getMemberUserIds().contains(currentUser.getId())) {
            server.getMemberUserIds().add(currentUser.getId());
            server.getRolesByUserId().putIfAbsent(currentUser.getId(), Role.MEMBER);
            storage.saveServer(server);
            notifyListeners(new AppEvent(EventType.DATA_CHANGED, normalizedId, null, null));
        }
    }

    public synchronized Channel createChannel(String serverId, String channelName) {
        requireLoggedIn();
        WorkspaceServer server = memberServerOrThrow(serverId);
        Role role = server.roleOf(currentUser.getId());
        if (!(role == Role.OWNER || role == Role.ADMIN)) {
            throw new IllegalStateException("Only owner/admin can create channels");
        }

        String normalizedName = normalizeNonEmpty(channelName, "Channel name");
        List<Channel> channels = channelsByServerId.computeIfAbsent(serverId, ignored -> new ArrayList<>());
        boolean duplicate = channels.stream().anyMatch(channel -> channel.getName().equalsIgnoreCase(normalizedName));
        if (duplicate) {
            throw new IllegalArgumentException("Channel already exists");
        }

        Channel channel = Channel.create(serverId, null, normalizedName);
        channels.add(channel);
        channels.sort(Comparator.comparing(Channel::getName, String.CASE_INSENSITIVE_ORDER));
        storage.saveChannel(channel);
        notifyListeners(new AppEvent(EventType.DATA_CHANGED, serverId, channel.getId(), null));
        return channel;
    }

    public synchronized List<WorkspaceServer> serversForCurrentUser() {
        if (currentUser == null) {
            return List.of();
        }
        return serversById.values().stream()
            .filter(server -> server.isMember(currentUser.getId()))
            .sorted(Comparator.comparing(WorkspaceServer::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public synchronized List<Channel> channelsForServer(String serverId) {
        if (currentUser == null || serverId == null) {
            return List.of();
        }
        WorkspaceServer server = serversById.get(serverId);
        if (server == null || !server.isMember(currentUser.getId())) {
            return List.of();
        }

        return channelsByServerId.getOrDefault(serverId, List.of()).stream()
            .sorted(Comparator.comparing(Channel::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public synchronized List<ChatMessage> messagesForChannel(String serverId, String channelId) {
        if (currentUser == null || serverId == null || channelId == null) {
            return List.of();
        }
        memberServerOrThrow(serverId);
        String key = channelKey(serverId, channelId);
        return new ArrayList<>(messagesByChannelKey.computeIfAbsent(key, ignored -> {
            List<ChatMessage> loaded = storage.loadMessages(serverId, channelId);
            loaded.sort(Comparator.comparing(ChatMessage::getCreatedAt));
            return loaded;
        }));
    }

    public synchronized ChatMessage sendMessage(String serverId, String channelId, String content) {
        requireLoggedIn();
        memberServerOrThrow(serverId);
        String normalizedContent = normalizeNonEmpty(content, "Message");

        Channel channel = channelsByServerId.getOrDefault(serverId, List.of())
            .stream()
            .filter(candidate -> Objects.equals(candidate.getId(), channelId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Channel not found"));

        ChatMessage message = ChatMessage.create(
            serverId,
            channel.getId(),
            currentUser.getId(),
            currentUser.getUsername(),
            normalizedContent
        );

        String key = channelKey(serverId, channel.getId());
        messagesByChannelKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(message);
        storage.appendMessage(message);

        if (!key.equals(activeChannelKey)) {
            unreadByChannelKey.merge(key, 1, Integer::sum);
            notifyListeners(new AppEvent(EventType.UNREAD_CHANGED, serverId, channel.getId(), null));
        }
        notifyListeners(new AppEvent(EventType.MESSAGE_POSTED, serverId, channel.getId(), message));
        return message;
    }

    public synchronized void markChannelRead(String serverId, String channelId) {
        if (currentUser == null || serverId == null || channelId == null) {
            return;
        }
        activeChannelKey = channelKey(serverId, channelId);
        unreadByChannelKey.put(activeChannelKey, 0);
        notifyListeners(new AppEvent(EventType.UNREAD_CHANGED, serverId, channelId, null));
    }

    public synchronized int unreadCount(String serverId, String channelId) {
        if (serverId == null || channelId == null) {
            return 0;
        }
        return unreadByChannelKey.getOrDefault(channelKey(serverId, channelId), 0);
    }

    public synchronized int totalUnread() {
        return unreadByChannelKey.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void addListener(Consumer<AppEvent> listener) {
        listeners.add(listener);
    }

    private void notifyListeners(AppEvent event) {
        for (Consumer<AppEvent> listener : listeners) {
            listener.accept(event);
        }
    }

    private String channelKey(String serverId, String channelId) {
        return serverId + ":" + channelId;
    }

    private void requireLoggedIn() {
        if (currentUser == null) {
            throw new IllegalStateException("Login required");
        }
    }

    private WorkspaceServer memberServerOrThrow(String serverId) {
        String normalizedId = normalizeNonEmpty(serverId, "Server ID");
        WorkspaceServer server = serversById.get(normalizedId);
        if (server == null) {
            throw new IllegalArgumentException("Server not found");
        }
        if (currentUser == null || !server.isMember(currentUser.getId())) {
            throw new IllegalStateException("You are not a member of this server");
        }
        return server;
    }

    private String normalizeUsername(String username) {
        return normalizeNonEmpty(username, "Username");
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

    private String normalizeNonEmpty(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        return value.trim();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
    }

    private void loadServerData() {
        serversById.clear();
        serverIdByInviteCode.clear();
        channelsByServerId.clear();
        for (WorkspaceServer server : storage.loadServers()) {
            serversById.put(server.getId(), server);
            if (ensureInviteCode(server)) {
                storage.saveServer(server);
            }
            List<Channel> channels = new ArrayList<>(storage.loadChannels(server.getId()));
            channels.sort(Comparator.comparing(Channel::getName, String.CASE_INSENSITIVE_ORDER));
            channelsByServerId.put(server.getId(), channels);
        }
    }
}
