package com.discord.discord_lite.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class UserProfileDetails {
    private String id;
    private String name;
    private String username;
    private boolean online;
    private UserStatus status;
    private String profileImageBase64;
    private Instant createdAt;
    private boolean blockedByRequester;
    private boolean blockedRequester;
    private List<String> mutualServerNames = new ArrayList<>();

    public UserProfileDetails() {
    }

    public UserProfileDetails(
        String id,
        String name,
        String username,
        boolean online,
        UserStatus status,
        String profileImageBase64,
        Instant createdAt,
        boolean blockedByRequester,
        boolean blockedRequester,
        List<String> mutualServerNames
    ) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.online = online;
        this.status = status;
        this.profileImageBase64 = profileImageBase64;
        this.createdAt = createdAt;
        this.blockedByRequester = blockedByRequester;
        this.blockedRequester = blockedRequester;
        setMutualServerNames(mutualServerNames);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isBlockedByRequester() {
        return blockedByRequester;
    }

    public void setBlockedByRequester(boolean blockedByRequester) {
        this.blockedByRequester = blockedByRequester;
    }

    public boolean isBlockedRequester() {
        return blockedRequester;
    }

    public void setBlockedRequester(boolean blockedRequester) {
        this.blockedRequester = blockedRequester;
    }

    public List<String> getMutualServerNames() {
        return mutualServerNames;
    }

    public void setMutualServerNames(List<String> mutualServerNames) {
        this.mutualServerNames = mutualServerNames == null ? new ArrayList<>() : new ArrayList<>(mutualServerNames);
    }

    public String displayName() {
        return name == null || name.isBlank() ? username : name;
    }
}
