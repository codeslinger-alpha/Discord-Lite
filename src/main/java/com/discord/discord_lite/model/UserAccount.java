package com.discord.discord_lite.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UserAccount {
    private String id;
    private String name;
    private String username;
    private String passwordHash;
    private String salt;
    private String profileImageBase64;
    private Set<String> blockedUserIds = new HashSet<>();
    private Map<String, Instant> lastReadChannelAtByKey = new HashMap<>();
    private Map<String, Instant> lastReadDmAtByPeerUserId = new HashMap<>();
    private UserStatus status;
    private Instant createdAt;

    public UserAccount() {
    }

    public UserAccount(
        String id,
        String name,
        String username,
        String passwordHash,
        String salt,
        String profileImageBase64,
        Set<String> blockedUserIds,
        Map<String, Instant> lastReadChannelAtByKey,
        Map<String, Instant> lastReadDmAtByPeerUserId,
        UserStatus status,
        Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.profileImageBase64 = profileImageBase64;
        this.blockedUserIds = blockedUserIds;
        this.lastReadChannelAtByKey = lastReadChannelAtByKey;
        this.lastReadDmAtByPeerUserId = lastReadDmAtByPeerUserId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static UserAccount create(String name, String username, String passwordHash, String salt) {
        return new UserAccount(
            UUID.randomUUID().toString(),
            name,
            username,
            passwordHash,
            salt,
            null,
            new HashSet<>(),
            new HashMap<>(),
            new HashMap<>(),
            UserStatus.ACTIVE,
            Instant.now()
        );
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getProfileImageBase64() {
        return profileImageBase64;
    }

    public void setProfileImageBase64(String profileImageBase64) {
        this.profileImageBase64 = profileImageBase64;
    }

    public Set<String> getBlockedUserIds() {
        if (blockedUserIds == null) {
            blockedUserIds = new HashSet<>();
        }
        return blockedUserIds;
    }

    public void setBlockedUserIds(Set<String> blockedUserIds) {
        this.blockedUserIds = blockedUserIds == null ? new HashSet<>() : new HashSet<>(blockedUserIds);
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Map<String, Instant> getLastReadChannelAtByKey() {
        if (lastReadChannelAtByKey == null) {
            lastReadChannelAtByKey = new HashMap<>();
        }
        return lastReadChannelAtByKey;
    }

    public void setLastReadChannelAtByKey(Map<String, Instant> lastReadChannelAtByKey) {
        this.lastReadChannelAtByKey = lastReadChannelAtByKey == null
            ? new HashMap<>()
            : new HashMap<>(lastReadChannelAtByKey);
    }

    public Map<String, Instant> getLastReadDmAtByPeerUserId() {
        if (lastReadDmAtByPeerUserId == null) {
            lastReadDmAtByPeerUserId = new HashMap<>();
        }
        return lastReadDmAtByPeerUserId;
    }

    public void setLastReadDmAtByPeerUserId(Map<String, Instant> lastReadDmAtByPeerUserId) {
        this.lastReadDmAtByPeerUserId = lastReadDmAtByPeerUserId == null
            ? new HashMap<>()
            : new HashMap<>(lastReadDmAtByPeerUserId);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
