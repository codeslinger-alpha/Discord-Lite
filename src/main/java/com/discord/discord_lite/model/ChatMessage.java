package com.discord.discord_lite.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatMessage {
    private String id;
    private String serverId;
    private String channelId;
    private String senderUserId;
    private String senderUsername;
    private String content;
    private MessageReply replyTo;
    private List<MessageAttachment> attachments = new ArrayList<>();
    private List<MessageReaction> reactions = new ArrayList<>();
    private Instant createdAt;
    private Instant editedAt;

    public ChatMessage() {
    }

    public ChatMessage(
        String id,
        String serverId,
        String channelId,
        String senderUserId,
        String senderUsername,
        String content,
        MessageReply replyTo,
        List<MessageAttachment> attachments,
        List<MessageReaction> reactions,
        Instant createdAt
    ) {
        this(id, serverId, channelId, senderUserId, senderUsername, content, replyTo, attachments, reactions, createdAt, null);
    }

    public ChatMessage(
        String id,
        String serverId,
        String channelId,
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
        this.serverId = serverId;
        this.channelId = channelId;
        this.senderUserId = senderUserId;
        this.senderUsername = senderUsername;
        this.content = content;
        this.replyTo = replyTo;
        setAttachments(attachments);
        setReactions(reactions);
        this.createdAt = createdAt;
        this.editedAt = editedAt;
    }

    public ChatMessage(
        String id,
        String serverId,
        String channelId,
        String senderUserId,
        String senderUsername,
        String content,
        List<MessageReaction> reactions,
        Instant createdAt
    ) {
        this(id, serverId, channelId, senderUserId, senderUsername, content, null, List.of(), reactions, createdAt, null);
    }

    public static ChatMessage create(
        String serverId,
        String channelId,
        String senderUserId,
        String senderUsername,
        String content
    ) {
        return create(serverId, channelId, senderUserId, senderUsername, content, null, List.of());
    }

    public static ChatMessage create(
        String serverId,
        String channelId,
        String senderUserId,
        String senderUsername,
        String content,
        MessageReply replyTo
    ) {
        return create(serverId, channelId, senderUserId, senderUsername, content, replyTo, List.of());
    }

    public static ChatMessage create(
        String serverId,
        String channelId,
        String senderUserId,
        String senderUsername,
        String content,
        MessageReply replyTo,
        List<MessageAttachment> attachments
    ) {
        return new ChatMessage(
            UUID.randomUUID().toString(),
            serverId,
            channelId,
            senderUserId,
            senderUsername,
            content,
            replyTo,
            attachments,
            List.of(),
            Instant.now()
        );
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

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
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
