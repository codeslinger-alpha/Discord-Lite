package com.discord.discord_lite.persistence;

import com.discord.discord_lite.model.Channel;
import com.discord.discord_lite.model.ChannelGroup;
import com.discord.discord_lite.model.ChatMessage;
import com.discord.discord_lite.model.DirectMessage;
import com.discord.discord_lite.model.UserAccount;
import com.discord.discord_lite.model.WorkspaceServer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class FileStorage {
    private static final TypeReference<List<UserAccount>> USER_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper mapper;
    private final Path dataRoot;

    public FileStorage(Path dataRoot) {
        this.dataRoot = dataRoot;
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public synchronized List<UserAccount> loadUsers() {
        Path usersFile = usersFile();
        if (!Files.exists(usersFile)) {
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(usersFile.toFile(), USER_LIST_TYPE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load users from " + usersFile, ex);
        }
    }

    public synchronized void saveUsers(List<UserAccount> users) {
        writeJsonAtomic(usersFile(), users);
    }

    public synchronized List<WorkspaceServer> loadServers() {
        Path serversDir = serversDir();
        if (!Files.exists(serversDir)) {
            return new ArrayList<>();
        }

        List<WorkspaceServer> servers = new ArrayList<>();
        try (Stream<Path> entries = Files.list(serversDir)) {
            entries
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(serverDirectory -> {
                    Path serverFile = serverDirectory.resolve("server.json");
                    if (Files.exists(serverFile)) {
                        try {
                            WorkspaceServer server = mapper.readValue(serverFile.toFile(), WorkspaceServer.class);
                            servers.add(server);
                        } catch (IOException ex) {
                            throw new UncheckedIOException("Failed to load server metadata: " + serverFile, ex);
                        }
                    }
                });
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to list server directories", ex);
        }
        return servers;
    }

    public synchronized void saveServer(WorkspaceServer server) {
        Path serverFile = serverDir(server.getId()).resolve("server.json");
        writeJsonAtomic(serverFile, server);
    }

    public synchronized void deleteServer(String serverId) {
        Path directory = serverDir(serverId);
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new UncheckedIOException("Failed to delete " + path, ex);
                }
            });
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to delete server directory " + directory, ex);
        }
    }

    public synchronized List<Channel> loadChannels(String serverId) {
        Path channelsDir = serverChannelsDir(serverId);
        if (!Files.exists(channelsDir)) {
            return new ArrayList<>();
        }

        List<Channel> channels = new ArrayList<>();
        try (Stream<Path> entries = Files.list(channelsDir)) {
            entries
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".json"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(channelFile -> {
                    try {
                        Channel channel = mapper.readValue(channelFile.toFile(), Channel.class);
                        channels.add(channel);
                    } catch (IOException ex) {
                        throw new UncheckedIOException("Failed to load channel metadata: " + channelFile, ex);
                    }
                });
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to list channel metadata files", ex);
        }
        return channels;
    }

    public synchronized void saveChannel(Channel channel) {
        Path channelFile = serverChannelsDir(channel.getServerId()).resolve(channel.getId() + ".json");
        writeJsonAtomic(channelFile, channel);
    }

    public synchronized List<ChannelGroup> loadChannelGroups(String serverId) {
        Path groupsDir = serverChannelGroupsDir(serverId);
        if (!Files.exists(groupsDir)) {
            return new ArrayList<>();
        }

        List<ChannelGroup> groups = new ArrayList<>();
        try (Stream<Path> entries = Files.list(groupsDir)) {
            entries
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".json"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(groupFile -> {
                    try {
                        ChannelGroup group = mapper.readValue(groupFile.toFile(), ChannelGroup.class);
                        groups.add(group);
                    } catch (IOException ex) {
                        throw new UncheckedIOException("Failed to load channel group metadata: " + groupFile, ex);
                    }
                });
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to list channel group metadata files", ex);
        }
        return groups;
    }

    public synchronized void saveChannelGroup(ChannelGroup group) {
        Path groupFile = serverChannelGroupsDir(group.getServerId()).resolve(group.getId() + ".json");
        writeJsonAtomic(groupFile, group);
    }

    public synchronized List<ChatMessage> loadMessages(String serverId, String channelId) {
        Path messageFile = messageFile(serverId, channelId);
        if (!Files.exists(messageFile)) {
            return new ArrayList<>();
        }

        List<ChatMessage> messages = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(messageFile, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    messages.add(mapper.readValue(line, ChatMessage.class));
                }
            }
            return messages;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load messages from " + messageFile, ex);
        }
    }

    public synchronized void appendMessage(ChatMessage message) {
        Path messageFile = messageFile(message.getServerId(), message.getChannelId());
        ensureDirectory(messageFile.getParent());
        try {
            String jsonLine = mapper.writeValueAsString(message) + System.lineSeparator();
            Files.writeString(
                messageFile,
                jsonLine,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to append message to " + messageFile, ex);
        }
    }

    public synchronized void saveMessages(String serverId, String channelId, List<ChatMessage> messages) {
        writeJsonLinesAtomic(messageFile(serverId, channelId), messages);
    }

    public synchronized List<DirectMessage> loadDirectMessages(String userIdA, String userIdB) {
        Path file = dmConversationFile(userIdA, userIdB);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        List<DirectMessage> messages = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    messages.add(mapper.readValue(line, DirectMessage.class));
                }
            }
            return messages;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load direct messages from " + file, ex);
        }
    }

    public synchronized void appendDirectMessage(DirectMessage message) {
        Path file = dmConversationFile(message.getUserAId(), message.getUserBId());
        ensureDirectory(file.getParent());
        try {
            String jsonLine = mapper.writeValueAsString(message) + System.lineSeparator();
            Files.writeString(file, jsonLine, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to append direct message to " + file, ex);
        }
    }

    public synchronized void saveDirectMessages(String userIdA, String userIdB, List<DirectMessage> messages) {
        writeJsonLinesAtomic(dmConversationFile(userIdA, userIdB), messages);
    }

    public synchronized void deleteDirectConversation(String userIdA, String userIdB) {
        Path file = dmConversationFile(userIdA, userIdB);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to delete direct message conversation " + file, ex);
        }
    }

    public synchronized List<String[]> listDirectMessagePairs() {
        Path dmDir = dmDir();
        if (!Files.exists(dmDir)) {
            return new ArrayList<>();
        }

        List<String[]> pairs = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dmDir)) {
            entries
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".jsonl"))
                .forEach(file -> {
                    String name = file.getFileName().toString();
                    String withoutExt = name.substring(0, name.length() - ".jsonl".length());
                    String[] ids = withoutExt.split("__", 2);
                    if (ids.length == 2 && !ids[0].isBlank() && !ids[1].isBlank()) {
                        pairs.add(new String[] {ids[0], ids[1]});
                    }
                });
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to list DM conversation files", ex);
        }
        return pairs;
    }

    private void writeJsonAtomic(Path target, Object data) {
        ensureDirectory(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), data);
            moveAtomic(tmp, target);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write JSON file " + target, ex);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }

    private void writeJsonLinesAtomic(Path target, List<?> rows) {
        ensureDirectory(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            List<String> lines = new ArrayList<>();
            for (Object row : rows) {
                lines.add(mapper.writeValueAsString(row));
            }
            Files.write(tmp, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            moveAtomic(tmp, target);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write JSONL file " + target, ex);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }

    private void moveAtomic(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to create directory " + directory, ex);
        }
    }

    private Path usersFile() {
        return dataRoot.resolve("users").resolve("users.json");
    }

    private Path serversDir() {
        return dataRoot.resolve("servers");
    }

    private Path serverDir(String serverId) {
        return serversDir().resolve(serverId);
    }

    private Path serverChannelsDir(String serverId) {
        return serverDir(serverId).resolve("channels");
    }

    private Path serverChannelGroupsDir(String serverId) {
        return serverDir(serverId).resolve("channel-groups");
    }

    private Path serverMessagesDir(String serverId) {
        return serverDir(serverId).resolve("messages");
    }

    private Path messageFile(String serverId, String channelId) {
        return serverMessagesDir(serverId).resolve(channelId + ".jsonl");
    }

    private Path dmDir() {
        return dataRoot.resolve("dm");
    }

    private Path dmConversationFile(String userIdA, String userIdB) {
        String[] pair = sortedPair(userIdA, userIdB);
        return dmDir().resolve(pair[0] + "__" + pair[1] + ".jsonl");
    }

    private String[] sortedPair(String first, String second) {
        String[] ids = new String[] {first, second};
        Arrays.sort(ids);
        return ids;
    }
}
