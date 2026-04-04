package com.discord.discord_lite.network;

import com.fasterxml.jackson.databind.JsonNode;

public class Packet {
    public String type;
    public String action;
    public String requestId;
    public Boolean ok;
    public String error;
    public JsonNode payload;

    public static Packet request(String action, String requestId, JsonNode payload) {
        Packet packet = new Packet();
        packet.type = "request";
        packet.action = action;
        packet.requestId = requestId;
        packet.payload = payload;
        return packet;
    }

    public static Packet response(String requestId, boolean ok, String error, JsonNode payload) {
        Packet packet = new Packet();
        packet.type = "response";
        packet.requestId = requestId;
        packet.ok = ok;
        packet.error = error;
        packet.payload = payload;
        return packet;
    }

    public static Packet event(String action, JsonNode payload) {
        Packet packet = new Packet();
        packet.type = "event";
        packet.action = action;
        packet.payload = payload;
        return packet;
    }
}
