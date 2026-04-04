package com.discord.discord_lite.model;

import java.util.UUID;

public class MessageAttachment {
    private String id;
    private String fileName;
    private String contentType;
    private long sizeBytes;
    private String base64Content;

    public MessageAttachment() {
    }

    public MessageAttachment(String id, String fileName, String contentType, long sizeBytes, String base64Content) {
        this.id = id;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.base64Content = base64Content;
    }

    public static MessageAttachment create(String fileName, String contentType, long sizeBytes, String base64Content) {
        return new MessageAttachment(UUID.randomUUID().toString(), fileName, contentType, sizeBytes, base64Content);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getBase64Content() {
        return base64Content;
    }

    public void setBase64Content(String base64Content) {
        this.base64Content = base64Content;
    }
}
