package com.discord.discord_lite.model;

import java.util.HashMap;
import java.util.Map;

public class UnreadStateSnapshot {
    private Map<String, Integer> channelUnreadCounts = new HashMap<>();
    private Map<String, Integer> dmUnreadCounts = new HashMap<>();

    public UnreadStateSnapshot() {
    }

    public UnreadStateSnapshot(Map<String, Integer> channelUnreadCounts, Map<String, Integer> dmUnreadCounts) {
        setChannelUnreadCounts(channelUnreadCounts);
        setDmUnreadCounts(dmUnreadCounts);
    }

    public Map<String, Integer> getChannelUnreadCounts() {
        if (channelUnreadCounts == null) {
            channelUnreadCounts = new HashMap<>();
        }
        return channelUnreadCounts;
    }

    public void setChannelUnreadCounts(Map<String, Integer> channelUnreadCounts) {
        this.channelUnreadCounts = channelUnreadCounts == null
            ? new HashMap<>()
            : new HashMap<>(channelUnreadCounts);
    }

    public Map<String, Integer> getDmUnreadCounts() {
        if (dmUnreadCounts == null) {
            dmUnreadCounts = new HashMap<>();
        }
        return dmUnreadCounts;
    }

    public void setDmUnreadCounts(Map<String, Integer> dmUnreadCounts) {
        this.dmUnreadCounts = dmUnreadCounts == null
            ? new HashMap<>()
            : new HashMap<>(dmUnreadCounts);
    }
}
