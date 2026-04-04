package com.discord.discord_lite.server;

import com.discord.discord_lite.model.Channel;
import com.discord.discord_lite.model.ChatMessage;
import com.discord.discord_lite.model.DirectMessage;
import com.discord.discord_lite.model.MessageAttachment;
import com.discord.discord_lite.model.PresenceUpdate;
import com.discord.discord_lite.model.TypingSignal;
import com.discord.discord_lite.model.UnreadStateSnapshot;
import com.discord.discord_lite.model.UserAccount;
import com.discord.discord_lite.model.UserProfileDetails;
import com.discord.discord_lite.model.UserStatus;
import com.discord.discord_lite.model.UserSummary;
import com.discord.discord_lite.model.WorkspaceServer;
import com.discord.discord_lite.network.JsonSupport;
import com.discord.discord_lite.network.Packet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LanChatServer {
    private static final TypeReference<List<MessageAttachment>> ATTACHMENT_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper mapper = JsonSupport.createMapper();
    private final LanServerState state;
    private final int port;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Set<ClientSession> sessions = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<ClientSession>> sessionsByUserId = new HashMap<>();
    private final Map<String, Integer> onlineConnectionCountByUserId = new HashMap<>();

    public LanChatServer(Path dataRoot, int port) {
        this.state = new LanServerState(dataRoot);
        this.port = port;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Discord Lite LAN server listening on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                ClientSession session = new ClientSession(socket);
                sessions.add(session);
                executor.submit(session);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private Set<String> onlineUserIdsSnapshot() {
        synchronized (this) {
            return new HashSet<>(onlineConnectionCountByUserId.keySet());
        }
    }

    private void registerSessionUser(ClientSession session, UserAccount user) {
        PresenceUpdate presenceUpdate = null;
        synchronized (this) {
            if (session.user != null && Objects.equals(session.user.getId(), user.getId())) {
                return;
            }
            unregisterSessionUserInternal(session);
            session.user = user;

            sessionsByUserId.computeIfAbsent(user.getId(), ignored -> new HashSet<>()).add(session);
            int next = onlineConnectionCountByUserId.getOrDefault(user.getId(), 0) + 1;
            onlineConnectionCountByUserId.put(user.getId(), next);
            if (next == 1) {
                presenceUpdate = state.presenceForUser(user.getId(), onlineUserIdsSnapshot());
            }
        }
        if (presenceUpdate != null) {
            broadcastToAuthenticated("presence", presenceUpdate);
        }
    }

    private void unregisterSessionUser(ClientSession session) {
        PresenceUpdate presenceUpdate = null;
        synchronized (this) {
            presenceUpdate = unregisterSessionUserInternal(session);
        }
        if (presenceUpdate != null) {
            broadcastToAuthenticated("presence", presenceUpdate);
        }
    }

    private PresenceUpdate unregisterSessionUserInternal(ClientSession session) {
        if (session.user == null) {
            return null;
        }

        String userId = session.user.getId();
        Set<ClientSession> userSessions = sessionsByUserId.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessionsByUserId.remove(userId);
            }
        }

        int next = onlineConnectionCountByUserId.getOrDefault(userId, 1) - 1;
        if (next <= 0) {
            onlineConnectionCountByUserId.remove(userId);
            session.user = null;
            if (state.userById(userId).isEmpty()) {
                return null;
            }
            return state.presenceForUser(userId, onlineUserIdsSnapshot());
        }
        onlineConnectionCountByUserId.put(userId, next);
        session.user = null;
        return null;
    }

    private void onDisconnect(ClientSession session) {
        sessions.remove(session);
        unregisterSessionUser(session);
    }

    private void broadcastToAuthenticated(String action, Object payload) {
        for (ClientSession session : sessions) {
            if (session.user != null) {
                session.sendEvent(action, payload);
            }
        }
    }

    private void broadcastToUsers(Set<String> userIds, String action, Object payload) {
        Set<ClientSession> targets = new HashSet<>();
        synchronized (this) {
            for (String userId : userIds) {
                Set<ClientSession> userSessions = sessionsByUserId.get(userId);
                if (userSessions != null) {
                    targets.addAll(userSessions);
                }
            }
        }
        for (ClientSession session : targets) {
            session.sendEvent(action, payload);
        }
    }

    private void closeOtherSessionsForUser(String userId, ClientSession keepSession) {
        Set<ClientSession> userSessions;
        synchronized (this) {
            userSessions = new HashSet<>(sessionsByUserId.getOrDefault(userId, Set.of()));
        }
        for (ClientSession session : userSessions) {
            if (session != keepSession) {
                session.closeSession();
            }
        }
    }

    private final class ClientSession implements Runnable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;
        private final Object writeLock = new Object();
        private volatile UserAccount user;

        private ClientSession(Socket socket) throws IOException {
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public void run() {
            try (socket; reader; writer) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleLine(line);
                }
            } catch (IOException ignored) {
            } finally {
                onDisconnect(this);
            }
        }

        private void handleLine(String line) {
            try {
                Packet packet = mapper.readValue(line, Packet.class);
                if (!"request".equals(packet.type)) {
                    return;
                }
                handleRequest(packet);
            } catch (RuntimeException | IOException ex) {
                sendResponseError(null, ex.getMessage());
            }
        }

        private void handleRequest(Packet request) {
            try {
                switch (request.action) {
                    case "register" -> {
                        String name = requireText(request.payload, "name");
                        String username = requireText(request.payload, "username");
                        String password = requireText(request.payload, "password");
                        UserAccount account = state.register(name, username, password);
                        registerSessionUser(this, account);
                        sendResponseOk(
                            request.requestId,
                            state.summaryForUser(account.getId(), account.getId(), onlineUserIdsSnapshot())
                        );
                    }
                    case "login" -> {
                        String username = requireText(request.payload, "username");
                        String password = requireText(request.payload, "password");
                        UserAccount account = state.login(username, password);
                        registerSessionUser(this, account);
                        sendResponseOk(
                            request.requestId,
                            state.summaryForUser(account.getId(), account.getId(), onlineUserIdsSnapshot())
                        );
                    }
                    case "suggest_usernames" -> {
                        String base = optionalText(request.payload, "base");
                        sendResponseOk(request.requestId, state.suggestUsernames(base));
                    }
                    case "logout" -> {
                        unregisterSessionUser(this);
                        sendResponseOk(request.requestId, null);
                    }
                    case "list_servers" -> {
                        UserAccount account = requireAuthenticated();
                        List<WorkspaceServer> servers = state.listServersForUser(account.getId());
                        sendResponseOk(request.requestId, servers);
                    }
                    case "create_server" -> {
                        UserAccount account = requireAuthenticated();
                        String name = requireText(request.payload, "name");
                        WorkspaceServer server = state.createServer(name, account.getId());
                        sendResponseOk(request.requestId, server);
                        broadcastToUsers(Set.of(account.getId()), "data_changed", NullNode.getInstance());
                    }
                    case "update_server_appearance" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        String iconImageBase64 = optionalText(request.payload, "iconImageBase64");
                        String coverImageBase64 = optionalText(request.payload, "coverImageBase64");
                        WorkspaceServer server = state.updateServerAppearance(
                            serverId,
                            account.getId(),
                            iconImageBase64,
                            coverImageBase64
                        );
                        sendResponseOk(request.requestId, server);
                        broadcastToUsers(state.memberIds(serverId), "data_changed", NullNode.getInstance());
                    }
                    case "list_banned_users" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        List<UserSummary> users = state.listBannedUsers(serverId, account.getId(), onlineUserIdsSnapshot());
                        sendResponseOk(request.requestId, users);
                    }
                    case "kick_member" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        String targetUserId = requireText(request.payload, "targetUserId");
                        Set<String> affectedUsers = new HashSet<>(state.memberIds(serverId));
                        state.kickMember(serverId, account.getId(), targetUserId);
                        affectedUsers.addAll(state.memberIds(serverId));
                        affectedUsers.add(targetUserId);
                        sendResponseOk(request.requestId, null);
                        broadcastToUsers(affectedUsers, "data_changed", NullNode.getInstance());
                    }
                    case "ban_member" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        String targetUserId = requireText(request.payload, "targetUserId");
                        Set<String> affectedUsers = new HashSet<>(state.memberIds(serverId));
                        state.banMember(serverId, account.getId(), targetUserId);
                        affectedUsers.addAll(state.memberIds(serverId));
                        affectedUsers.add(targetUserId);
                        sendResponseOk(request.requestId, null);
                        broadcastToUsers(affectedUsers, "data_changed", NullNode.getInstance());
                    }
                    case "unban_member" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        String targetUserId = requireText(request.payload, "targetUserId");
                        state.unbanMember(serverId, account.getId(), targetUserId);
                        sendResponseOk(request.requestId, null);
                        broadcastToUsers(Set.of(account.getId(), targetUserId), "data_changed", NullNode.getInstance());
                    }
                    case "join_server" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        state.joinServer(serverId, account.getId());
                        sendResponseOk(request.requestId, null);
                        broadcastToUsers(Set.of(account.getId()), "data_changed", NullNode.getInstance());
                    }
                    case "leave_server" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        Set<String> affectedUsers = new HashSet<>(state.memberIds(serverId));
                        state.leaveServer(serverId, account.getId());
                        affectedUsers.addAll(state.memberIds(serverId));
                        affectedUsers.add(account.getId());
                        sendResponseOk(request.requestId, null);
                        broadcastToUsers(affectedUsers, "data_changed", NullNode.getInstance());
                    }
                    case "delete_server" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        Set<String> affectedUsers = state.deleteServer(serverId, account.getId());
                        sendResponseOk(request.requestId, null);
                        broadcastToUsers(affectedUsers, "data_changed", NullNode.getInstance());
                    }
                    case "list_channels" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        List<Channel> channels = state.listChannels(serverId, account.getId());
                        sendResponseOk(request.requestId, channels);
                    }
                    case "list_channel_groups", "list_groups" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        sendResponseOk(request.requestId, state.listChannelGroups(serverId, account.getId()));
                    }
                    case "create_channel_group", "create_group" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        String name = requireText(request.payload, "name");
                        sendResponseOk(request.requestId, state.createChannelGroup(serverId, account.getId(), name));
                        broadcastToUsers(state.memberIds(serverId), "data_changed", NullNode.getInstance());
                    }
                    case "list_server_members" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        List<UserSummary> members = state.listServerMembers(
                            serverId,
                            account.getId(),
                            onlineUserIdsSnapshot()
                        );
                        sendResponseOk(request.requestId, members);
                    }
                    case "list_channel_members", "list_members" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        String channelId = optionalText(request.payload, "channelId");
                        List<UserSummary> members = channelId == null
                            ? state.listServerMembers(serverId, account.getId(), onlineUserIdsSnapshot())
                            : state.listChannelMembers(serverId, channelId, account.getId(), onlineUserIdsSnapshot());
                        sendResponseOk(request.requestId, members);
                    }
                    case "create_channel" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        String name = requireText(request.payload, "name");
                        String groupId = optionalText(request.payload, "groupId");
                        Channel channel = state.createChannel(serverId, account.getId(), name, groupId);
                        sendResponseOk(request.requestId, channel);
                        broadcastToUsers(state.memberIds(serverId), "data_changed", NullNode.getInstance());
                    }
                    case "list_messages" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        String channelId = requireText(request.payload, "channelId");
                        List<ChatMessage> messages = state.listChannelMessages(serverId, channelId, account.getId());
                        sendResponseOk(request.requestId, messages);
                    }
                    case "get_unread_state" -> {
                        UserAccount account = requireAuthenticated();
                        UnreadStateSnapshot unreadState = state.unreadStateForUser(account.getId());
                        sendResponseOk(request.requestId, unreadState);
                    }
                    case "mark_channel_read" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        String channelId = requireText(request.payload, "channelId");
                        state.markChannelRead(account.getId(), serverId, channelId);
                        sendResponseOk(request.requestId, null);
                    }
                    case "send_message" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        String channelId = requireText(request.payload, "channelId");
                        String content = optionalText(request.payload, "content");
                        String replyToMessageId = optionalText(request.payload, "replyToMessageId");
                        List<MessageAttachment> attachments = parseAttachments(request.payload);
                        ChatMessage message = state.sendChannelMessage(
                            serverId,
                            channelId,
                            account,
                            content,
                            replyToMessageId,
                            attachments
                        );
                        sendResponseOk(request.requestId, message);
                        broadcastToUsers(state.memberIds(serverId), "channel_message", message);
                    }
                    case "edit_channel_message" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        String channelId = requireText(request.payload, "channelId");
                        String messageId = requireText(request.payload, "messageId");
                        String content = requireText(request.payload, "content");
                        ChatMessage message = state.editChannelMessage(
                            serverId,
                            channelId,
                            messageId,
                            account.getId(),
                            content
                        );
                        sendResponseOk(request.requestId, message);
                        broadcastToUsers(state.memberIds(serverId), "data_changed", NullNode.getInstance());
                    }
                    case "delete_channel_message" -> {
                        UserAccount account = requireAuthenticated();
                        String serverId = requireText(request.payload, "serverId");
                        String channelId = requireText(request.payload, "channelId");
                        String messageId = requireText(request.payload, "messageId");
                        state.deleteChannelMessage(serverId, channelId, messageId, account.getId());
                        sendResponseOk(request.requestId, null);
                        broadcastToUsers(state.memberIds(serverId), "data_changed", NullNode.getInstance());
                    }
                    case "list_users", "list_user" -> {
                        UserAccount account = requireAuthenticated();
                        List<UserSummary> users = state.listUsers(account.getId(), onlineUserIdsSnapshot());
                        sendResponseOk(request.requestId, users);
                    }
                    case "open_dm" -> {
                        UserAccount account = requireAuthenticated();
                        String targetUserId = requireText(request.payload, "targetUserId");
                        state.openDirectMessage(account.getId(), targetUserId);
                        sendResponseOk(request.requestId, null);
                        broadcastToUsers(Set.of(account.getId(), targetUserId), "data_changed", NullNode.getInstance());
                    }
                    case "list_dm_peers" -> {
                        UserAccount account = requireAuthenticated();
                        List<UserSummary> peers = state.listDmPeers(account.getId(), onlineUserIdsSnapshot());
                        sendResponseOk(request.requestId, peers);
                    }
                    case "get_user_profile" -> {
                        UserAccount account = requireAuthenticated();
                        String targetUserId = requireText(request.payload, "targetUserId");
                        UserProfileDetails profile = state.profileForUser(
                            targetUserId,
                            account.getId(),
                            onlineUserIdsSnapshot()
                        );
                        sendResponseOk(request.requestId, profile);
                    }
                    case "list_dm_messages" -> {
                        UserAccount account = requireAuthenticated();
                        String targetUserId = requireText(request.payload, "targetUserId");
                        List<DirectMessage> messages = state.listDirectMessages(account.getId(), targetUserId);
                        sendResponseOk(request.requestId, messages);
                    }
                    case "mark_dm_read" -> {
                        UserAccount account = requireAuthenticated();
                        String targetUserId = requireText(request.payload, "targetUserId");
                        state.markDmRead(account.getId(), targetUserId);
                        sendResponseOk(request.requestId, null);
                    }
                    case "send_dm" -> {
                        UserAccount account = requireAuthenticated();
                        String targetUserId = requireText(request.payload, "targetUserId");
                        String content = optionalText(request.payload, "content");
                        String replyToMessageId = optionalText(request.payload, "replyToMessageId");
                        List<MessageAttachment> attachments = parseAttachments(request.payload);
                        DirectMessage message = state.sendDirectMessage(
                            account,
                            targetUserId,
                            content,
                            replyToMessageId,
                            attachments
                        );
                        sendResponseOk(request.requestId, message);
                        broadcastToUsers(Set.of(account.getId(), targetUserId), "dm_message", message);
                    }
                    case "edit_dm_message" -> {
                        UserAccount account = requireAuthenticated();
                        String targetUserId = requireText(request.payload, "targetUserId");
                        String messageId = requireText(request.payload, "messageId");
                        String content = requireText(request.payload, "content");
                        DirectMessage message = state.editDirectMessage(
                            account.getId(),
                            targetUserId,
                            messageId,
                            content
                        );
                        sendResponseOk(request.requestId, message);
                        broadcastToUsers(Set.of(account.getId(), targetUserId), "data_changed", NullNode.getInstance());
                    }
                    case "delete_dm_message" -> {
                        UserAccount account = requireAuthenticated();
                        String targetUserId = requireText(request.payload, "targetUserId");
                        String messageId = requireText(request.payload, "messageId");
                        state.deleteDirectMessage(account.getId(), targetUserId, messageId);
                        sendResponseOk(request.requestId, null);
                        broadcastToUsers(Set.of(account.getId(), targetUserId), "data_changed", NullNode.getInstance());
                    }
                    case "delete_dm_conversation" -> {
                        UserAccount account = requireAuthenticated();
                        String targetUserId = requireText(request.payload, "targetUserId");
                        state.deleteDirectConversation(account.getId(), targetUserId);
                        sendResponseOk(request.requestId, null);
                        broadcastToUsers(Set.of(account.getId(), targetUserId), "data_changed", NullNode.getInstance());
                    }
                    case "block_user" -> {
                        UserAccount account = requireAuthenticated();
                        String targetUserId = requireText(request.payload, "targetUserId");
                        state.blockUser(account.getId(), targetUserId);
                        sendResponseOk(request.requestId, null);
                        broadcastToUsers(Set.of(account.getId(), targetUserId), "data_changed", NullNode.getInstance());
                    }
                    case "unblock_user" -> {
                        UserAccount account = requireAuthenticated();
                        String targetUserId = requireText(request.payload, "targetUserId");
                        state.unblockUser(account.getId(), targetUserId);
                        sendResponseOk(request.requestId, null);
                        broadcastToUsers(Set.of(account.getId(), targetUserId), "data_changed", NullNode.getInstance());
                    }
                    case "toggle_reaction" -> {
                        UserAccount account = requireAuthenticated();
                        String scope = requireText(request.payload, "scope").toLowerCase(Locale.ROOT);
                        String messageId = requireText(request.payload, "messageId");
                        String emoji = requireText(request.payload, "emoji");
                        switch (scope) {
                            case "channel" -> {
                                String serverId = requireText(request.payload, "serverId");
                                String channelId = requireText(request.payload, "channelId");
                                ChatMessage message = state.toggleChannelReaction(
                                    serverId,
                                    channelId,
                                    messageId,
                                    account.getId(),
                                    emoji
                                );
                                sendResponseOk(request.requestId, message);
                                broadcastToUsers(state.memberIds(serverId), "data_changed", NullNode.getInstance());
                            }
                            case "dm" -> {
                                String targetUserId = requireText(request.payload, "targetUserId");
                                DirectMessage message = state.toggleDirectReaction(
                                    account.getId(),
                                    targetUserId,
                                    messageId,
                                    emoji
                                );
                                sendResponseOk(request.requestId, message);
                                broadcastToUsers(Set.of(account.getId(), targetUserId), "data_changed", NullNode.getInstance());
                            }
                            default -> throw new IllegalArgumentException("Unknown reaction scope");
                        }
                    }
                    case "update_profile" -> {
                        UserAccount account = requireAuthenticated();
                        String name = requireText(request.payload, "name");
                        String currentPassword = optionalText(request.payload, "currentPassword");
                        String newPassword = optionalText(request.payload, "newPassword");
                        String profileImageBase64 = optionalText(request.payload, "profileImageBase64");
                        String statusText = optionalText(request.payload, "status");
                        UserStatus status = statusText == null
                            ? account.getStatus()
                            : UserStatus.valueOf(statusText.trim().toUpperCase(Locale.ROOT));
                        UserAccount updated = state.updateProfile(
                            account.getId(),
                            name,
                            currentPassword,
                            newPassword,
                            status,
                            profileImageBase64
                        );
                        this.user = updated;
                        sendResponseOk(
                            request.requestId,
                            state.summaryForUser(updated.getId(), updated.getId(), onlineUserIdsSnapshot())
                        );
                        broadcastToAuthenticated("presence", state.presenceForUser(updated.getId(), onlineUserIdsSnapshot()));
                    }
                    case "delete_account" -> {
                        UserAccount account = requireAuthenticated();
                        String currentPassword = requireText(request.payload, "currentPassword");
                        String userId = account.getId();
                        state.deleteAccount(userId, currentPassword);
                        unregisterSessionUser(this);
                        sendResponseOk(request.requestId, null);
                        closeOtherSessionsForUser(userId, this);
                        broadcastToAuthenticated("data_changed", NullNode.getInstance());
                    }
                    case "typing" -> {
                        UserAccount account = requireAuthenticated();
                        String scope = requireText(request.payload, "scope");
                        boolean active = requireBoolean(request.payload, "active");

                        if ("channel".equals(scope)) {
                            String serverId = requireText(request.payload, "serverId");
                            String channelId = requireText(request.payload, "channelId");
                            state.listChannelMessages(serverId, channelId, account.getId());
                            TypingSignal signal = new TypingSignal(
                                scope,
                                serverId,
                                channelId,
                                null,
                                account.getId(),
                                account.getName(),
                                active
                            );
                            broadcastToUsers(state.memberIds(serverId), "typing", signal);
                        } else if ("dm".equals(scope)) {
                            String targetUserId = requireText(request.payload, "targetUserId");
                            state.openDirectMessage(account.getId(), targetUserId);
                            TypingSignal signal = new TypingSignal(
                                scope,
                                null,
                                null,
                                targetUserId,
                                account.getId(),
                                account.getName(),
                                active
                            );
                            broadcastToUsers(Set.of(account.getId(), targetUserId), "typing", signal);
                        } else {
                            throw new IllegalArgumentException("Unknown typing scope");
                        }
                        sendResponseOk(request.requestId, null);
                    }
                    default -> sendResponseError(request.requestId, "Unknown action: " + request.action);
                }
            } catch (RuntimeException ex) {
                sendResponseError(request.requestId, ex.getMessage());
            }
        }

        private UserAccount requireAuthenticated() {
            if (user == null) {
                throw new IllegalStateException("Login required");
            }
            return user;
        }

        private String requireText(JsonNode node, String fieldName) {
            if (node == null || node.get(fieldName) == null || node.get(fieldName).asText().isBlank()) {
                throw new IllegalArgumentException(fieldName + " is required");
            }
            return node.get(fieldName).asText().trim();
        }

        private String optionalText(JsonNode node, String fieldName) {
            if (node == null || node.get(fieldName) == null) {
                return null;
            }
            String value = node.get(fieldName).asText();
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        }

        private boolean requireBoolean(JsonNode node, String fieldName) {
            if (node == null || node.get(fieldName) == null || !node.get(fieldName).isBoolean()) {
                throw new IllegalArgumentException(fieldName + " must be a boolean");
            }
            return node.get(fieldName).asBoolean();
        }

        private List<MessageAttachment> parseAttachments(JsonNode node) {
            if (node == null || node.get("attachments") == null || node.get("attachments").isNull()) {
                return List.of();
            }
            return mapper.convertValue(node.get("attachments"), ATTACHMENT_LIST_TYPE);
        }

        private void sendResponseOk(String requestId, Object payload) {
            JsonNode payloadNode = payload == null ? NullNode.getInstance() : mapper.valueToTree(payload);
            send(Packet.response(requestId, true, null, payloadNode));
        }

        private void sendResponseError(String requestId, String errorMessage) {
            send(Packet.response(requestId, false, errorMessage == null ? "Unknown error" : errorMessage, NullNode.getInstance()));
        }

        private void sendEvent(String action, Object payload) {
            JsonNode payloadNode = payload == null ? NullNode.getInstance() : mapper.valueToTree(payload);
            send(Packet.event(action, payloadNode));
        }

        private void send(Packet packet) {
            synchronized (writeLock) {
                try {
                    writer.write(mapper.writeValueAsString(packet));
                    writer.write('\n');
                    writer.flush();
                } catch (IOException ignored) {
                }
            }
        }

        private void closeSession() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
