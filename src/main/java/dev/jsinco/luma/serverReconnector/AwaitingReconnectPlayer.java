package dev.jsinco.luma.serverReconnector;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

public class AwaitingReconnectPlayer {

    private final Player player;
    private final RegisteredServer server;
    private final long disconnectTime;

    public AwaitingReconnectPlayer(Player player, RegisteredServer server, long disconnectTime) {
        this.player = player;
        this.server = server;
        this.disconnectTime = disconnectTime;
    }

    public Player getPlayer() {
        return player;
    }

    public RegisteredServer getServer() {
        return server;
    }

    public long getDisconnectTime() {
        return disconnectTime;
    }
}
