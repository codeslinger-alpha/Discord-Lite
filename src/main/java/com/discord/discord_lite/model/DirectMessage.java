package com.discord.discord_lite.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DirectMessage {
    private String id;
    private String userAId;
    private String userBId;
    private String senderUserId;
    private String senderUsername;
    private String content;
    private MessageReply replyTo;
    private List<MessageAttachment> attachments = new ArrayList<>();
    private List<MessageReaction> reactions = new ArrayList<>();
    private Instant createdAt;
    private Instant editedAt;

    public DirectMessage() {
    }

    public DirectMessage(
        String id,
        String userAId,
        String userBId,
        String senderUserId,
        String senderUsername,
        String content,
        MessageReply replyTo,
        List<MessageAttachment> attachments,
        List<MessageReaction> reactions,
        Instant createdAt
    ) {
        this(id, userAId, userBId, senderUserId, senderUsername, content, replyTo, attachments, reactions, createdAt, null);
    }

    public DirectMessage(
        String id,
        String userAId,
        String userBId,
        String senderUserId,
        String senderUsername,
        String content,
        MessageReply replyTo,
        List<MessageAttachment> attachments,
        List<MessageReaction> reactions,
        Instant createdAt,
        Instant editedAt
    ) {
        this.id = id;
        this.userAId = userAId;
        this.userBId = userBId;
        this.senderUserId = senderUserId;
        this.senderUsername = senderUsername;
        this.content = content;
        this.replyTo = replyTo;
        setAttachments(attachments);
        setReactions(reactions);
        this.createdAt = createdAt;
        this.editedAt = editedAt;
    }

    public DirectMessage(
        String id,
        String userAId,
        String userBId,
        String senderUserId,
        String senderUsername,
        String content,
        List<MessageReaction> reactions,
        Instant createdAt
    ) {
        this(id, userAId, userBId, senderUserId, senderUsername, content, null, List.of(), reactions, createdAt, null);
    }

    public static DirectMessage create(
        String userAId,
        String userBId,
        String senderUserId,
        String senderUsername,
        String content
    ) {
        return create(userAId, userBId, senderUserId, senderUsername, content, null, List.of());
    }

    public static DirectMessage create(
        String userAId,
        String userBId,
        String senderUserId,
        String senderUsername,
        String content,
        MessageReply replyTo
    ) {
        return create(userAId, userBId, senderUserId, senderUsername, content, replyTo, List.of());
    }

    public static DirectMessage create(
        String userAId,
        String userBId,
        String senderUserId,
        String senderUsername,
        String content,
        MessageReply replyTo,
        List<MessageAttachment> attachments
    ) {
        return new DirectMessage(
            UUID.randomUUID().toString(),
            userAId,
            userBId,
            senderUserId,
            senderUsername,
            content,
            replyTo,
            attachments,
            List.of(),
            Instant.now()
        );
    }

    public String counterpartFor(String userId) {
        return userAId.equals(userId) ? userBId : userAId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserAId() {
        return userAId;
    }

    public void setUserAId(String userAId) {
        this.userAId = userAId;
    }

    public String getUserBId() {
        return userBId;
    }

    public void setUserBId(String userBId) {
        this.userBId = userBId;
    }

    public String getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(String senderUserId) {
        this.senderUserId = senderUserId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MessageReply getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(MessageReply replyTo) {
        this.replyTo = replyTo;
    }

    public List<MessageAttachment> getAttachments() {
        if (attachments == null) {
            attachments = new ArrayList<>();
        }
        return attachments;
    }

    public void setAttachments(List<MessageAttachment> attachments) {
        this.attachments = attachments == null ? new ArrayList<>() : new ArrayList<>(attachments);
    }

    public List<MessageReaction> getReactions() {
        if (reactions == null) {
            reactions = new ArrayList<>();
        }
        return reactions;
    }

    public void setReactions(List<MessageReaction> reactions) {
        this.reactions = reactions == null ? new ArrayList<>() : new ArrayList<>(reactions);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(Instant editedAt) {
        this.editedAt = editedAt;
    }
}
