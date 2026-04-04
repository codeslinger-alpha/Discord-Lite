package com.discord.discord_lite.model;

public class MessageReply {
    private String messageId;
    private String senderUserId;
    private String senderName;
    private String content;
    private int attachmentCount;
    private boolean imageAttachment;

    public MessageReply() {
    }

    public MessageReply(String messageId, String senderUserId, String senderName, String content) {
        this(messageId, senderUserId, senderName, content, 0, false);
    }

    public MessageReply(
        String messageId,
        String senderUserId,
        String senderName,
        String content,
        int attachmentCount,
        boolean imageAttachment
    ) {
        this.messageId = messageId;
        this.senderUserId = senderUserId;
        this.senderName = senderName;
        this.content = content;
        this.attachmentCount = attachmentCount;
        this.imageAttachment = imageAttachment;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(String senderUserId) {
        this.senderUserId = senderUserId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getAttachmentCount() {
        return attachmentCount;
    }

    public void setAttachmentCount(int attachmentCount) {
        this.attachmentCount = attachmentCount;
    }

    public boolean isImageAttachment() {
        return imageAttachment;
    }

    public void setImageAttachment(boolean imageAttachment) {
        this.imageAttachment = imageAttachment;
    }
}
