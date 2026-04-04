package com.discord.discord_lite.model;

public class PresenceUpdate {
    private String userId;
    private String name;
    private String username;
    private boolean online;
    private UserStatus status;
    private String profileImageBase64;

    public PresenceUpdate() {
    }

    public PresenceUpdate(
        String userId,
        String name,
        String username,
        boolean online,
        UserStatus status,
        String profileImageBase64
    ) {
        this.userId = userId;
        this.name = name;
        this.username = username;
        this.online = online;
        this.status = status;
        this.profileImageBase64 = profileImageBase64;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public String getProfileImageBase64() {
        return profileImageBase64;
    }

    public void setProfileImageBase64(String profileImageBase64) {
        this.profileImageBase64 = profileImageBase64;
    }
}
