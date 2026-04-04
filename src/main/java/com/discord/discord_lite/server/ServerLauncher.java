package com.discord.discord_lite.server;

import java.nio.file.Path;

public final class ServerLauncher {
    private ServerLauncher() {
    }

    public static void main(String[] args) throws Exception {
        int port = 5555;
        Path dataRoot = Path.of("data");

        if (args.length >= 1 && !args[0].isBlank()) {
            port = Integer.parseInt(args[0].trim());
        }
        if (args.length >= 2 && !args[1].isBlank()) {
            dataRoot = Path.of(args[1].trim());
        }

        LanChatServer server = new LanChatServer(dataRoot, port);
        server.start();
    }
}
