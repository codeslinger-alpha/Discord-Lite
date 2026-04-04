package com.discord.discord_lite.model;

import java.time.Instant;
import java.util.UUID;

public class Channel {
    private String id;
    private String serverId;
    private String groupId;
    private String name;
    private Instant createdAt;

    public Channel() {
    }

    public Channel(String id, String serverId, String groupId, String name, Instant createdAt) {
        this.id = id;
        this.serverId = serverId;
        this.groupId = groupId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public static Channel create(String serverId, String groupId, String name) {
        return new Channel(UUID.randomUUID().toString(), serverId, groupId, name, Instant.now());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "#" + name;
    }
}
