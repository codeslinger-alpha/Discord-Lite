package com.discord.discord_lite.model;

import java.util.ArrayList;
import java.util.List;

public class MessageReaction {
    private String emoji;
    private List<String> userIds = new ArrayList<>();

    public MessageReaction() {
    }

    public MessageReaction(String emoji, List<String> userIds) {
        this.emoji = emoji;
        setUserIds(userIds);
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public List<String> getUserIds() {
        if (userIds == null) {
            userIds = new ArrayList<>();
        }
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds == null ? new ArrayList<>() : new ArrayList<>(userIds);
    }
}
