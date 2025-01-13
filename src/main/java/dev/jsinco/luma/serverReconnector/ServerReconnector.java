package dev.jsinco.luma.serverReconnector;

import com.google.inject.Inject;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(id = "server-reconnector", name = "server-reconnector", version = BuildConstants.VERSION, authors = {"Jsinco"})
public class ServerReconnector {

    private static final long GIVE_UP_DELAY = 300000L; // 5 minutes
    private static final ConcurrentHashMap<UUID, AwaitingReconnectPlayer> awaitingReconnectPlayers = new ConcurrentHashMap<>();

    @Inject
    private Logger logger;
    @Inject
    private ProxyServer proxy;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("server-reconnector has been initialized.");

        proxy.getScheduler().buildTask(this, () -> {
            if (awaitingReconnectPlayers.isEmpty()) {
                return;
            }

            for (var entry : awaitingReconnectPlayers.entrySet()) {
                AwaitingReconnectPlayer aRP = entry.getValue();
                RegisteredServer server = aRP.getServer();
                server.ping(PingOptions.DEFAULT).whenComplete((status, throwable) -> {
                    if (status == null) {
                        if (System.currentTimeMillis() - aRP.getDisconnectTime() > GIVE_UP_DELAY) {
                            awaitingReconnectPlayers.remove(entry.getKey());
                            logger.info("Player {} has been waiting for too long, giving up.", aRP.getPlayer().getUsername());
                        }
                        return;
                    }

                    awaitingReconnectPlayers.remove(entry.getKey());
                    logger.info("Reconnected player {} to server {}", aRP.getPlayer().getUsername(), server.getServerInfo().getName());

                    Player player = aRP.getPlayer();
                    if (player.getCurrentServer().isPresent() && player.getCurrentServer().get().getServer() == server) {
                        logger.info("Player {} is already connected to server {}", player.getUsername(), server.getServerInfo().getName());
                        return;
                    }
                    try {
                        player.createConnectionRequest(server).connect().whenComplete((result, throwable1) -> {
                            player.sendMessage(MiniMessage.miniMessage().deserialize("<#f498f6>Sending you to <#E2E2E2>" + server.getServerInfo().getName() + "<#f498f6>!"));
                        });
                    } catch (Exception e) {
                        logger.error("Failed to reconnect player {} to server {}", aRP.getPlayer().getUsername(), server.getServerInfo().getName(), e);
                    }
                });
            }
        }).repeat(40, TimeUnit.SECONDS).schedule();
    }

    @Subscribe
    public void onPlayerDisconnectEvent(DisconnectEvent event) {
        if (awaitingReconnectPlayers.containsKey(event.getPlayer().getUniqueId())) {
            logger.info("Player {} disconnected from the proxy, giving up.", event.getPlayer().getUsername());
            awaitingReconnectPlayers.remove(event.getPlayer().getUniqueId());
        }
    }

    @Subscribe
    public void onPlayerKickedFromServer(KickedFromServerEvent event) {
        proxy.getScheduler().buildTask(this, () -> {
            if (!event.getPlayer().isActive()) {
                logger.info("Player {} was kicked from server {} but is no longer active.",
                        event.getPlayer().getUsername(), event.getServer().getServerInfo().getName());
                return;
            }

            RegisteredServer server = event.getServer();

            server.ping(PingOptions.DEFAULT).whenComplete((status, throwable) -> {
                if (status == null) {
                    logger.info("Scheduled reconnection for player {} to server {}", event.getPlayer().getUsername(), server.getServerInfo().getName());

                    AwaitingReconnectPlayer awaitingReconnectPlayer =
                            new AwaitingReconnectPlayer(event.getPlayer(), event.getServer(), System.currentTimeMillis());
                    awaitingReconnectPlayers.put(event.getPlayer().getUniqueId(), awaitingReconnectPlayer);
                }
            });

        }).delay(5, TimeUnit.SECONDS).schedule();
    }
}
