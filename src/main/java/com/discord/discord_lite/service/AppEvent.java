package com.discord.discord_lite.service;

import com.discord.discord_lite.model.ChatMessage;

public record AppEvent(EventType type, String serverId, String channelId, ChatMessage message) {
}
