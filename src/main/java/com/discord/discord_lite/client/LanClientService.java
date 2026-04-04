package com.discord.discord_lite.client;

import com.discord.discord_lite.model.Channel;
import com.discord.discord_lite.model.ChannelGroup;
import com.discord.discord_lite.model.ChatMessage;
import com.discord.discord_lite.model.DirectMessage;
import com.discord.discord_lite.model.MessageAttachment;
import com.discord.discord_lite.model.PresenceUpdate;
import com.discord.discord_lite.model.TypingSignal;
import com.discord.discord_lite.model.UnreadStateSnapshot;
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
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class LanClientService {
    private static final TypeReference<List<WorkspaceServer>> SERVER_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Channel>> CHANNEL_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ChannelGroup>> CHANNEL_GROUP_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ChatMessage>> CHAT_MESSAGE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<UserSummary>> USER_SUMMARY_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<DirectMessage>> DIRECT_MESSAGE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper mapper = JsonSupport.createMapper();
    private final List<Consumer<ClientEvent>> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, CompletableFuture<Packet>> pendingResponses = new ConcurrentHashMap<>();
    private final Map<String, Integer> unreadByChannel = new ConcurrentHashMap<>();
    private final Map<String, Integer> unreadByDmUser = new ConcurrentHashMap<>();
    private final Map<String, Map<String, TypingEntry>> typingByConversation = new ConcurrentHashMap<>();
    private final Map<String, UserSummary> knownUsersById = new ConcurrentHashMap<>();
    private final Object sendLock = new Object();

    private volatile Socket socket;
    private volatile BufferedReader reader;
    private volatile BufferedWriter writer;
    private volatile Thread readerThread;
    private volatile boolean connected;
    private volatile UserSummary currentUser;
    private volatile String activeChannelKey;
    private volatile String activeDmUserId;

    public void connect(String host, int port) {
        disconnect();
        try {
            Socket nextSocket = new Socket();
            nextSocket.connect(new InetSocketAddress(host, port), 4000);
            nextSocket.setTcpNoDelay(true);
            BufferedReader nextReader = new BufferedReader(
                new InputStreamReader(nextSocket.getInputStream(), StandardCharsets.UTF_8)
            );
            BufferedWriter nextWriter = new BufferedWriter(
                new OutputStreamWriter(nextSocket.getOutputStream(), StandardCharsets.UTF_8)
            );

            socket = nextSocket;
            reader = nextReader;
            writer = nextWriter;
            connected = true;
            startReaderLoop();
            notifyListeners(new ClientEvent(ClientEventType.CONNECTION_CHANGED, null));
        } catch (IOException ex) {
            disconnect();
            throw new IllegalStateException("Unable to connect to server", ex);
        }
    }

    public void disconnect() {
        boolean hadConnection = connected;
        connected = false;
        closeQuietly(socket);
        closeQuietly(reader);
        closeQuietly(writer);
        socket = null;
        reader = null;
        writer = null;

        for (CompletableFuture<Packet> pending : pendingResponses.values()) {
            pending.completeExceptionally(new IllegalStateException("Connection closed"));
        }
        pendingResponses.clear();

        currentUser = null;
        activeChannelKey = null;
        activeDmUserId = null;
        unreadByChannel.clear();
        unreadByDmUser.clear();
        typingByConversation.clear();
        if (hadConnection) {
            notifyListeners(new ClientEvent(ClientEventType.CONNECTION_CHANGED, null));
            notifyListeners(new ClientEvent(ClientEventType.SESSION_CHANGED, null));
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public Optional<UserSummary> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    public String currentDisplayName() {
        return currentUser == null ? "Guest" : currentUser.displayName();
    }

    public String currentUsername() {
        return currentUser == null ? "guest" : currentUser.getUsername();
    }

    public void addListener(Consumer<ClientEvent> listener) {
        listeners.add(listener);
    }

    public void register(String name, String username, String password) {
        JsonNode payload = sendRequest("register", Map.of("name", name, "username", username, "password", password));
        UserSummary user = mapper.convertValue(payload, UserSummary.class);
        knownUsersById.put(user.getId(), user);
        currentUser = user;
        resetConversationState();
        applyUnreadState(new UnreadStateSnapshot(), false);
        notifyListeners(new ClientEvent(ClientEventType.SESSION_CHANGED, user));
    }

    public void login(String username, String password) {
        JsonNode payload = sendRequest("login", Map.of("username", username, "password", password));
        UserSummary user = mapper.convertValue(payload, UserSummary.class);
        knownUsersById.put(user.getId(), user);
        currentUser = user;
        resetConversationState();
        refreshUnreadState();
        notifyListeners(new ClientEvent(ClientEventType.SESSION_CHANGED, user));
    }

    public void logout() {
        if (connected) {
            try {
                sendRequest("logout", null);
            } catch (RuntimeException ignored) {
            }
        }
        currentUser = null;
        resetConversationState();
        notifyListeners(new ClientEvent(ClientEventType.SESSION_CHANGED, null));
    }

    public List<String> suggestUsernames(String base) {
        JsonNode payload = sendRequest("suggest_usernames", Map.of("base", base == null ? "" : base));
        return mapper.convertValue(payload, STRING_LIST_TYPE);
    }

    public List<WorkspaceServer> listServers() {
        requireSession();
        JsonNode payload = sendRequest("list_servers", null);
        return mapper.convertValue(payload, SERVER_LIST_TYPE);
    }

    public WorkspaceServer createServer(String name) {
        requireSession();
        JsonNode payload = sendRequest("create_server", Map.of("name", name));
        return mapper.convertValue(payload, WorkspaceServer.class);
    }

    public WorkspaceServer updateServerAppearance(String serverId, String iconImageBase64, String coverImageBase64) {
        requireSession();
        Map<String, Object> request = new HashMap<>();
        request.put("serverId", serverId);
        request.put("iconImageBase64", iconImageBase64 == null ? "" : iconImageBase64);
        request.put("coverImageBase64", coverImageBase64 == null ? "" : coverImageBase64);
        JsonNode payload = sendRequest("update_server_appearance", request);
        WorkspaceServer server = mapper.convertValue(payload, WorkspaceServer.class);
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, server));
        return server;
    }

    public List<UserSummary> listBannedUsers(String serverId) {
        requireSession();
        JsonNode payload = sendRequest("list_banned_users", Map.of("serverId", serverId));
        List<UserSummary> users = mapper.convertValue(payload, USER_SUMMARY_LIST_TYPE);
        users.forEach(user -> knownUsersById.put(user.getId(), user));
        return users;
    }

    public void kickMember(String serverId, String targetUserId) {
        requireSession();
        sendRequest("kick_member", Map.of("serverId", serverId, "targetUserId", targetUserId));
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, null));
    }

    public void banMember(String serverId, String targetUserId) {
        requireSession();
        sendRequest("ban_member", Map.of("serverId", serverId, "targetUserId", targetUserId));
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, null));
    }

    public void unbanMember(String serverId, String targetUserId) {
        requireSession();
        sendRequest("unban_member", Map.of("serverId", serverId, "targetUserId", targetUserId));
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, null));
    }

    public void leaveServer(String serverId) {
        requireSession();
        sendRequest("leave_server", Map.of("serverId", serverId));
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, null));
    }

    public void deleteServer(String serverId) {
        requireSession();
        sendRequest("delete_server", Map.of("serverId", serverId));
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, null));
    }

    public void joinServer(String serverId) {
        requireSession();
        sendRequest("join_server", Map.of("serverId", serverId));
    }

    public List<Channel> listChannels(String serverId) {
        requireSession();
        JsonNode payload = sendRequest("list_channels", Map.of("serverId", serverId));
        return mapper.convertValue(payload, CHANNEL_LIST_TYPE);
    }

    public List<ChannelGroup> listChannelGroups(String serverId) {
        requireSession();
        JsonNode payload;
        try {
            payload = sendRequest("list_channel_groups", Map.of("serverId", serverId));
        } catch (RuntimeException ex) {
            if (!isUnknownActionError(ex)) {
                throw ex;
            }
            payload = sendRequest("list_groups", Map.of("serverId", serverId));
        }
        return mapper.convertValue(payload, CHANNEL_GROUP_LIST_TYPE);
    }

    public List<UserSummary> listServerMembers(String serverId) {
        requireSession();
        JsonNode payload = sendRequest("list_server_members", Map.of("serverId", serverId));
        List<UserSummary> members = mapper.convertValue(payload, USER_SUMMARY_LIST_TYPE);
        members.forEach(user -> knownUsersById.put(user.getId(), user));
        return members;
    }

    public List<UserSummary> listChannelMembers(String serverId, String channelId) {
        requireSession();
        JsonNode payload;
        try {
            payload = sendRequest("list_channel_members", Map.of("serverId", serverId, "channelId", channelId));
        } catch (RuntimeException ex) {
            if (!isUnknownActionError(ex)) {
                throw ex;
            }
            payload = sendRequest("list_server_members", Map.of("serverId", serverId));
        }
        List<UserSummary> members = mapper.convertValue(payload, USER_SUMMARY_LIST_TYPE);
        members.forEach(user -> knownUsersById.put(user.getId(), user));
        return members;
    }

    public Channel createChannel(String serverId, String name) {
        return createChannel(serverId, name, null);
    }

    public Channel createChannel(String serverId, String name, String groupId) {
        requireSession();
        Map<String, Object> request = new HashMap<>();
        request.put("serverId", serverId);
        request.put("name", name);
        if (groupId != null && !groupId.isBlank()) {
            request.put("groupId", groupId);
        }
        JsonNode payload = sendRequest("create_channel", request);
        return mapper.convertValue(payload, Channel.class);
    }

    public ChannelGroup createChannelGroup(String serverId, String name) {
        requireSession();
        JsonNode payload;
        try {
            payload = sendRequest("create_channel_group", Map.of("serverId", serverId, "name", name));
        } catch (RuntimeException ex) {
            if (!isUnknownActionError(ex)) {
                throw ex;
            }
            payload = sendRequest("create_group", Map.of("serverId", serverId, "name", name));
        }
        return mapper.convertValue(payload, ChannelGroup.class);
    }

    public List<ChatMessage> listMessages(String serverId, String channelId) {
        requireSession();
        JsonNode payload = sendRequest("list_messages", Map.of("serverId", serverId, "channelId", channelId));
        List<ChatMessage> messages = mapper.convertValue(payload, CHAT_MESSAGE_LIST_TYPE);
        messages.sort(Comparator.comparing(ChatMessage::getCreatedAt));
        return messages;
    }

    public ChatMessage sendMessage(String serverId, String channelId, String content) {
        return sendMessage(serverId, channelId, content, null, List.of());
    }

    public ChatMessage sendMessage(String serverId, String channelId, String content, String replyToMessageId) {
        return sendMessage(serverId, channelId, content, replyToMessageId, List.of());
    }

    public ChatMessage sendMessage(
        String serverId,
        String channelId,
        String content,
        String replyToMessageId,
        List<MessageAttachment> attachments
    ) {
        requireSession();
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("serverId", serverId);
        request.put("channelId", channelId);
        request.put("content", content == null ? "" : content);
        if (replyToMessageId != null && !replyToMessageId.isBlank()) {
            request.put("replyToMessageId", replyToMessageId);
        }
        if (attachments != null && !attachments.isEmpty()) {
            request.put("attachments", attachments);
        }
        JsonNode payload = sendRequest("send_message", request);
        return mapper.convertValue(payload, ChatMessage.class);
    }

    public ChatMessage editChannelMessage(String serverId, String channelId, String messageId, String content) {
        requireSession();
        Map<String, String> request = new java.util.LinkedHashMap<>();
        request.put("serverId", serverId);
        request.put("channelId", channelId);
        request.put("messageId", messageId);
        request.put("content", content);
        JsonNode payload = sendRequest("edit_channel_message", request);
        ChatMessage message = mapper.convertValue(payload, ChatMessage.class);
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, null));
        return message;
    }

    public void deleteChannelMessage(String serverId, String channelId, String messageId) {
        requireSession();
        sendRequest("delete_channel_message", Map.of("serverId", serverId, "channelId", channelId, "messageId", messageId));
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, null));
    }

    public List<UserSummary> listUsers() {
        requireSession();
        JsonNode payload;
        try {
            payload = sendRequest("list_users", null);
        } catch (RuntimeException ex) {
            if (!isUnknownActionError(ex)) {
                throw ex;
            }
            payload = sendRequest("list_user", null);
        }
        List<UserSummary> users = mapper.convertValue(payload, USER_SUMMARY_LIST_TYPE);
        users.forEach(user -> knownUsersById.put(user.getId(), user));
        return users;
    }

    public void openDm(String targetUserId) {
        requireSession();
        sendRequest("open_dm", Map.of("targetUserId", targetUserId));
    }

    public List<UserSummary> listDmPeers() {
        requireSession();
        JsonNode payload = sendRequest("list_dm_peers", null);
        List<UserSummary> peers = mapper.convertValue(payload, USER_SUMMARY_LIST_TYPE);
        peers.forEach(user -> knownUsersById.put(user.getId(), user));
        return peers;
    }

    public UserProfileDetails getUserProfile(String targetUserId) {
        requireSession();
        JsonNode payload = sendRequest("get_user_profile", Map.of("targetUserId", targetUserId));
        UserProfileDetails profile = mapper.convertValue(payload, UserProfileDetails.class);
        if (profile != null) {
            knownUsersById.put(
                profile.getId(),
                new UserSummary(
                    profile.getId(),
                    profile.getName(),
                    profile.getUsername(),
                    profile.isOnline(),
                    profile.getStatus(),
                    profile.getProfileImageBase64()
                )
            );
        }
        return profile;
    }

    public List<DirectMessage> listDmMessages(String targetUserId) {
        requireSession();
        JsonNode payload = sendRequest("list_dm_messages", Map.of("targetUserId", targetUserId));
        List<DirectMessage> messages = mapper.convertValue(payload, DIRECT_MESSAGE_LIST_TYPE);
        messages.sort(Comparator.comparing(DirectMessage::getCreatedAt));
        return messages;
    }

    public DirectMessage sendDm(String targetUserId, String content) {
        return sendDm(targetUserId, content, null, List.of());
    }

    public DirectMessage sendDm(String targetUserId, String content, String replyToMessageId) {
        return sendDm(targetUserId, content, replyToMessageId, List.of());
    }

    public DirectMessage sendDm(
        String targetUserId,
        String content,
        String replyToMessageId,
        List<MessageAttachment> attachments
    ) {
        requireSession();
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("targetUserId", targetUserId);
        request.put("content", content == null ? "" : content);
        if (replyToMessageId != null && !replyToMessageId.isBlank()) {
            request.put("replyToMessageId", replyToMessageId);
        }
        if (attachments != null && !attachments.isEmpty()) {
            request.put("attachments", attachments);
        }
        JsonNode payload = sendRequest("send_dm", request);
        return mapper.convertValue(payload, DirectMessage.class);
    }

    public DirectMessage editDmMessage(String targetUserId, String messageId, String content) {
        requireSession();
        Map<String, String> request = new java.util.LinkedHashMap<>();
        request.put("targetUserId", targetUserId);
        request.put("messageId", messageId);
        request.put("content", content);
        JsonNode payload = sendRequest("edit_dm_message", request);
        DirectMessage message = mapper.convertValue(payload, DirectMessage.class);
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, null));
        return message;
    }

    public void deleteDmMessage(String targetUserId, String messageId) {
        requireSession();
        sendRequest("delete_dm_message", Map.of("targetUserId", targetUserId, "messageId", messageId));
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, null));
    }

    public void deleteDmConversation(String targetUserId) {
        requireSession();
        sendRequest("delete_dm_conversation", Map.of("targetUserId", targetUserId));
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, null));
    }

    public void blockUser(String targetUserId) {
        requireSession();
        sendRequest("block_user", Map.of("targetUserId", targetUserId));
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, null));
    }

    public void unblockUser(String targetUserId) {
        requireSession();
        sendRequest("unblock_user", Map.of("targetUserId", targetUserId));
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, null));
    }

    public ChatMessage toggleChannelReaction(String serverId, String channelId, String messageId, String emoji) {
        requireSession();
        JsonNode payload = sendRequest(
            "toggle_reaction",
            Map.of(
                "scope",
                "channel",
                "serverId",
                serverId,
                "channelId",
                channelId,
                "messageId",
                messageId,
                "emoji",
                emoji
            )
        );
        return mapper.convertValue(payload, ChatMessage.class);
    }

    public DirectMessage toggleDmReaction(String targetUserId, String messageId, String emoji) {
        requireSession();
        JsonNode payload = sendRequest(
            "toggle_reaction",
            Map.of(
                "scope",
                "dm",
                "targetUserId",
                targetUserId,
                "messageId",
                messageId,
                "emoji",
                emoji
            )
        );
        return mapper.convertValue(payload, DirectMessage.class);
    }

    public UserSummary updateProfile(
        String name,
        UserStatus status,
        String currentPassword,
        String newPassword,
        String profileImageBase64
    ) {
        requireSession();
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("status", status == null ? UserStatus.ACTIVE.name() : status.name());
        request.put("currentPassword", currentPassword == null ? "" : currentPassword);
        request.put("newPassword", newPassword == null ? "" : newPassword);
        request.put("profileImageBase64", profileImageBase64 == null ? "" : profileImageBase64);
        JsonNode payload = sendRequest("update_profile", request);
        UserSummary user = mapper.convertValue(payload, UserSummary.class);
        currentUser = user;
        knownUsersById.put(user.getId(), user);
        notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, user));
        return user;
    }

    public UserSummary updateStatus(UserStatus status) {
        requireSession();
        if (currentUser == null) {
            throw new IllegalStateException("Login required");
        }
        return updateProfile(
            currentUser.displayName(),
            status,
            "",
            "",
            currentUser.getProfileImageBase64()
        );
    }

    public void deleteAccount(String currentPassword) {
        requireSession();
        sendRequest("delete_account", Map.of("currentPassword", currentPassword == null ? "" : currentPassword));
        currentUser = null;
        resetConversationState();
        notifyListeners(new ClientEvent(ClientEventType.SESSION_CHANGED, null));
    }

    public void sendTypingChannel(String serverId, String channelId, boolean active) {
        if (!connected || currentUser == null) {
            return;
        }
        sendRequest("typing", Map.of("scope", "channel", "serverId", serverId, "channelId", channelId, "active", active));
    }

    public void sendTypingDm(String targetUserId, boolean active) {
        if (!connected || currentUser == null) {
            return;
        }
        sendRequest("typing", Map.of("scope", "dm", "targetUserId", targetUserId, "active", active));
    }

    public void refreshUnreadState() {
        requireSession();
        JsonNode payload = sendRequest("get_unread_state", null);
        UnreadStateSnapshot snapshot = mapper.convertValue(payload, UnreadStateSnapshot.class);
        applyUnreadState(snapshot, false);
    }

    public void markChannelRead(String serverId, String channelId) {
        String key = channelKey(serverId, channelId);
        activeChannelKey = key;
        activeDmUserId = null;
        unreadByChannel.put(key, 0);
        if (connected && currentUser != null) {
            sendRequest("mark_channel_read", Map.of("serverId", serverId, "channelId", channelId));
        }
        notifyListeners(new ClientEvent(ClientEventType.UNREAD_CHANGED, null));
    }

    public void markDmRead(String userId) {
        activeDmUserId = userId;
        activeChannelKey = null;
        unreadByDmUser.put(userId, 0);
        if (connected && currentUser != null) {
            sendRequest("mark_dm_read", Map.of("targetUserId", userId));
        }
        notifyListeners(new ClientEvent(ClientEventType.UNREAD_CHANGED, null));
    }

    public void setActiveChannel(String serverId, String channelId) {
        activeChannelKey = channelKey(serverId, channelId);
        activeDmUserId = null;
    }

    public void setActiveDm(String userId) {
        activeDmUserId = userId;
        activeChannelKey = null;
    }

    public void clearActiveConversation() {
        activeChannelKey = null;
        activeDmUserId = null;
    }

    public int unreadCountChannel(String serverId, String channelId) {
        return unreadByChannel.getOrDefault(channelKey(serverId, channelId), 0);
    }

    public int unreadCountDm(String userId) {
        return unreadByDmUser.getOrDefault(userId, 0);
    }

    public int unreadCountServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return 0;
        }
        String prefix = serverId + ":";
        return unreadByChannel.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(prefix))
            .mapToInt(Map.Entry::getValue)
            .sum();
    }

    public int totalUnreadDms() {
        return unreadByDmUser.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int totalUnread() {
        int channelUnread = unreadByChannel.values().stream().mapToInt(Integer::intValue).sum();
        int dmUnread = unreadByDmUser.values().stream().mapToInt(Integer::intValue).sum();
        return channelUnread + dmUnread;
    }

    public String typingTextForChannel(String serverId, String channelId) {
        return typingText("channel:" + serverId + ":" + channelId);
    }

    public String typingTextForDm(String peerUserId) {
        if (currentUser == null) {
            return "";
        }
        String[] pair = sortedPair(currentUser.getId(), peerUserId);
        return typingText("dm:" + pair[0] + ":" + pair[1]);
    }

    public Optional<UserSummary> knownUser(String userId) {
        return Optional.ofNullable(knownUsersById.get(userId));
    }

    private JsonNode sendRequest(String action, Object payload) {
        ensureConnected();
        String requestId = UUID.randomUUID().toString();
        JsonNode payloadNode = payload == null ? NullNode.getInstance() : mapper.valueToTree(payload);
        Packet packet = Packet.request(action, requestId, payloadNode);
        CompletableFuture<Packet> responseFuture = new CompletableFuture<>();
        pendingResponses.put(requestId, responseFuture);
        try {
            sendPacket(packet);
            Packet response = responseFuture.get(10, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(response.ok)) {
                throw new IllegalStateException(response.error == null ? "Server error" : response.error);
            }
            return response.payload == null ? NullNode.getInstance() : response.payload;
        } catch (TimeoutException ex) {
            pendingResponses.remove(requestId);
            throw new IllegalStateException("Request timed out");
        } catch (Exception ex) {
            pendingResponses.remove(requestId);
            throw ex instanceof RuntimeException runtime ? runtime : new IllegalStateException("Request failed", ex);
        }
    }

    private void sendPacket(Packet packet) {
        BufferedWriter currentWriter = writer;
        if (currentWriter == null) {
            throw new IllegalStateException("Not connected");
        }
        synchronized (sendLock) {
            try {
                currentWriter.write(mapper.writeValueAsString(packet));
                currentWriter.write('\n');
                currentWriter.flush();
            } catch (IOException ex) {
                disconnect();
                throw new IllegalStateException("Connection lost while sending request", ex);
            }
        }
    }

    private void startReaderLoop() {
        readerThread = new Thread(this::readLoop, "discord-lite-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        try {
            BufferedReader currentReader = reader;
            if (currentReader == null) {
                return;
            }
            String line;
            while ((line = currentReader.readLine()) != null) {
                Packet packet = mapper.readValue(line, Packet.class);
                if ("response".equals(packet.type)) {
                    CompletableFuture<Packet> future = pendingResponses.remove(packet.requestId);
                    if (future != null) {
                        future.complete(packet);
                    }
                } else if ("event".equals(packet.type)) {
                    onEvent(packet);
                }
            }
        } catch (Exception ex) {
            if (connected) {
                notifyListeners(new ClientEvent(ClientEventType.ERROR, "Connection closed"));
            }
        } finally {
            if (connected) {
                disconnect();
            }
        }
    }

    private void onEvent(Packet packet) {
        switch (packet.action) {
            case "data_changed" -> notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, null));
            case "channel_message" -> {
                ChatMessage message = mapper.convertValue(packet.payload, ChatMessage.class);
                if (currentUser != null && !message.getSenderUserId().equals(currentUser.getId())) {
                    String key = channelKey(message.getServerId(), message.getChannelId());
                    if (!key.equals(activeChannelKey)) {
                        unreadByChannel.merge(key, 1, Integer::sum);
                        notifyListeners(new ClientEvent(ClientEventType.UNREAD_CHANGED, null));
                    }
                }
                notifyListeners(new ClientEvent(ClientEventType.CHANNEL_MESSAGE, message));
            }
            case "dm_message" -> {
                DirectMessage message = mapper.convertValue(packet.payload, DirectMessage.class);
                if (currentUser != null) {
                    String peerId = message.counterpartFor(currentUser.getId());
                    if (!message.getSenderUserId().equals(currentUser.getId()) && !peerId.equals(activeDmUserId)) {
                        unreadByDmUser.merge(peerId, 1, Integer::sum);
                        notifyListeners(new ClientEvent(ClientEventType.UNREAD_CHANGED, null));
                    }
                    knownUsersById.computeIfAbsent(
                        message.getSenderUserId(),
                        ignored -> new UserSummary(
                            message.getSenderUserId(),
                            message.getSenderUsername(),
                            message.getSenderUsername(),
                            true,
                            UserStatus.ACTIVE,
                            null
                        )
                    );
                }
                notifyListeners(new ClientEvent(ClientEventType.DM_MESSAGE, message));
            }
            case "typing" -> {
                TypingSignal signal = mapper.convertValue(packet.payload, TypingSignal.class);
                if (currentUser != null && !Objects.equals(signal.getFromUserId(), currentUser.getId())) {
                    applyTypingSignal(signal);
                    notifyListeners(new ClientEvent(ClientEventType.TYPING_CHANGED, signal));
                }
            }
            case "presence" -> {
                PresenceUpdate update = mapper.convertValue(packet.payload, PresenceUpdate.class);
                UserSummary updated = new UserSummary(
                    update.getUserId(),
                    update.getName(),
                    update.getUsername(),
                    update.isOnline(),
                    update.getStatus(),
                    update.getProfileImageBase64()
                );
                knownUsersById.put(update.getUserId(), updated);
                if (currentUser != null && Objects.equals(currentUser.getId(), update.getUserId())) {
                    currentUser = updated;
                }
                notifyListeners(new ClientEvent(ClientEventType.DATA_CHANGED, update));
            }
            default -> {
            }
        }
    }

    private void applyTypingSignal(TypingSignal signal) {
        String key = typingConversationKey(signal);
        if (key == null) {
            return;
        }

        Map<String, TypingEntry> byUser = typingByConversation.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
        if (signal.isActive()) {
            byUser.put(signal.getFromUserId(), new TypingEntry(signal.getFromUsername(), Instant.now()));
        } else {
            byUser.remove(signal.getFromUserId());
            if (byUser.isEmpty()) {
                typingByConversation.remove(key);
            }
        }
    }

    private String typingText(String conversationKey) {
        Map<String, TypingEntry> byUser = typingByConversation.get(conversationKey);
        if (byUser == null || byUser.isEmpty()) {
            return "";
        }

        Instant now = Instant.now();
        List<String> names = new ArrayList<>();
        List<String> staleUsers = new ArrayList<>();
        for (Map.Entry<String, TypingEntry> entry : byUser.entrySet()) {
            if (Duration.between(entry.getValue().lastSeenAt(), now).toSeconds() > 4) {
                staleUsers.add(entry.getKey());
            } else {
                names.add(entry.getValue().username());
            }
        }
        staleUsers.forEach(byUser::remove);
        if (byUser.isEmpty()) {
            typingByConversation.remove(conversationKey);
        }

        if (names.isEmpty()) {
            return "";
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (names.size() == 1) {
            return names.get(0) + " is typing...";
        }
        if (names.size() == 2) {
            return names.get(0) + " and " + names.get(1) + " are typing...";
        }
        return "Several people are typing...";
    }

    private String typingConversationKey(TypingSignal signal) {
        if ("channel".equals(signal.getScope())) {
            if (signal.getServerId() == null || signal.getChannelId() == null) {
                return null;
            }
            return "channel:" + signal.getServerId() + ":" + signal.getChannelId();
        }
        if ("dm".equals(signal.getScope())) {
            if (currentUser == null) {
                return null;
            }
            String[] pair = sortedPair(currentUser.getId(), signal.getFromUserId());
            return "dm:" + pair[0] + ":" + pair[1];
        }
        return null;
    }

    private String[] sortedPair(String left, String right) {
        if (left.compareTo(right) <= 0) {
            return new String[] {left, right};
        }
        return new String[] {right, left};
    }

    private String channelKey(String serverId, String channelId) {
        return serverId + ":" + channelId;
    }

    private void applyUnreadState(UnreadStateSnapshot snapshot, boolean notify) {
        unreadByChannel.clear();
        unreadByDmUser.clear();
        if (snapshot != null) {
            unreadByChannel.putAll(snapshot.getChannelUnreadCounts());
            unreadByDmUser.putAll(snapshot.getDmUnreadCounts());
        }
        if (notify) {
            notifyListeners(new ClientEvent(ClientEventType.UNREAD_CHANGED, null));
        }
    }

    private void resetConversationState() {
        activeChannelKey = null;
        activeDmUserId = null;
        unreadByChannel.clear();
        unreadByDmUser.clear();
        typingByConversation.clear();
    }

    private void ensureConnected() {
        if (!connected) {
            throw new IllegalStateException("Not connected");
        }
    }

    private boolean isUnknownActionError(RuntimeException ex) {
        String message = rootMessage(ex);
        return message != null && message.toLowerCase().contains("unknown action");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private void requireSession() {
        ensureConnected();
        if (currentUser == null) {
            throw new IllegalStateException("Login required");
        }
    }

    private void notifyListeners(ClientEvent event) {
        for (Consumer<ClientEvent> listener : listeners) {
            listener.accept(event);
        }
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private record TypingEntry(String username, Instant lastSeenAt) {
    }
}
