package com.discord.discord_lite.model;

public class TypingSignal {
    private String scope;
    private String serverId;
    private String channelId;
    private String targetUserId;
    private String fromUserId;
    private String fromUsername;
    private boolean active;

    public TypingSignal() {
    }

    public TypingSignal(
        String scope,
        String serverId,
        String channelId,
        String targetUserId,
        String fromUserId,
        String fromUsername,
        boolean active
    ) {
        this.scope = scope;
        this.serverId = serverId;
        this.channelId = channelId;
        this.targetUserId = targetUserId;
        this.fromUserId = fromUserId;
        this.fromUsername = fromUsername;
        this.active = active;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getFromUsername() {
        return fromUsername;
    }

    public void setFromUsername(String fromUsername) {
        this.fromUsername = fromUsername;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
