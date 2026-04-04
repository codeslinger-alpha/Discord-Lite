package com.discord.discord_lite.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class WorkspaceServer {
    private static final char[] INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int INVITE_CODE_LENGTH = 8;

    private String id;
    private String name;
    private String ownerUserId;
    private Set<String> memberUserIds = new HashSet<>();
    private Set<String> bannedUserIds = new HashSet<>();
    private Map<String, Role> rolesByUserId = new HashMap<>();
    private Instant createdAt;
    private String inviteCode;
    private String iconImageBase64;
    private String coverImageBase64;

    public WorkspaceServer() {
    }

    public WorkspaceServer(
        String id,
        String name,
        String ownerUserId,
        Set<String> memberUserIds,
        Set<String> bannedUserIds,
        Map<String, Role> rolesByUserId,
        Instant createdAt
    ) {
        this(id, name, ownerUserId, memberUserIds, bannedUserIds, rolesByUserId, createdAt, null, null, null);
    }

    public WorkspaceServer(
        String id,
        String name,
        String ownerUserId,
        Set<String> memberUserIds,
        Set<String> bannedUserIds,
        Map<String, Role> rolesByUserId,
        Instant createdAt,
        String inviteCode
    ) {
        this(id, name, ownerUserId, memberUserIds, bannedUserIds, rolesByUserId, createdAt, inviteCode, null, null);
    }

    public WorkspaceServer(
        String id,
        String name,
        String ownerUserId,
        Set<String> memberUserIds,
        Set<String> bannedUserIds,
        Map<String, Role> rolesByUserId,
        Instant createdAt,
        String inviteCode,
        String iconImageBase64,
        String coverImageBase64
    ) {
        this.id = id;
        this.name = name;
        this.ownerUserId = ownerUserId;
        this.memberUserIds = memberUserIds;
        this.bannedUserIds = bannedUserIds;
        this.rolesByUserId = rolesByUserId;
        this.createdAt = createdAt;
        this.inviteCode = inviteCode;
        this.iconImageBase64 = iconImageBase64;
        this.coverImageBase64 = coverImageBase64;
    }

    public static WorkspaceServer create(String name, String ownerUserId) {
        WorkspaceServer server = new WorkspaceServer();
        server.id = UUID.randomUUID().toString();
        server.name = name;
        server.ownerUserId = ownerUserId;
        server.createdAt = Instant.now();
        server.memberUserIds.add(ownerUserId);
        server.bannedUserIds = new HashSet<>();
        server.rolesByUserId.put(ownerUserId, Role.OWNER);
        server.inviteCode = generateInviteCode();
        server.iconImageBase64 = null;
        server.coverImageBase64 = null;
        return server;
    }

    public static String generateInviteCode() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder builder = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            builder.append(INVITE_CODE_ALPHABET[random.nextInt(INVITE_CODE_ALPHABET.length)]);
        }
        return builder.toString();
    }

    public Role roleOf(String userId) {
        return rolesByUserId.getOrDefault(userId, Role.MEMBER);
    }

    public boolean isMember(String userId) {
        return memberUserIds.contains(userId);
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

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Set<String> getMemberUserIds() {
        return memberUserIds;
    }

    public void setMemberUserIds(Set<String> memberUserIds) {
        this.memberUserIds = memberUserIds;
    }

    public Set<String> getBannedUserIds() {
        if (bannedUserIds == null) {
            bannedUserIds = new HashSet<>();
        }
        return bannedUserIds;
    }

    public void setBannedUserIds(Set<String> bannedUserIds) {
        this.bannedUserIds = bannedUserIds == null ? new HashSet<>() : new HashSet<>(bannedUserIds);
    }

    public Map<String, Role> getRolesByUserId() {
        return rolesByUserId;
    }

    public void setRolesByUserId(Map<String, Role> rolesByUserId) {
        this.rolesByUserId = rolesByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getIconImageBase64() {
        return iconImageBase64;
    }

    public void setIconImageBase64(String iconImageBase64) {
        this.iconImageBase64 = iconImageBase64;
    }

    public String getCoverImageBase64() {
        return coverImageBase64;
    }

    public void setCoverImageBase64(String coverImageBase64) {
        this.coverImageBase64 = coverImageBase64;
    }

    @Override
    public String toString() {
        return name;
    }
}
