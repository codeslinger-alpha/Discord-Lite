package com.discord.discord_lite.model;

import java.time.Instant;
import java.util.UUID;

public class ChannelGroup {
    private String id;
    private String serverId;
    private String name;
    private int sortOrder;
    private Instant createdAt;

    public ChannelGroup() {
    }

    public ChannelGroup(String id, String serverId, String name, int sortOrder, Instant createdAt) {
        this.id = id;
        this.serverId = serverId;
        this.name = name;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public static ChannelGroup create(String serverId, String name, int sortOrder) {
        return new ChannelGroup(UUID.randomUUID().toString(), serverId, name, sortOrder, Instant.now());
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return name;
    }
}
