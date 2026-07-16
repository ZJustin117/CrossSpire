package crossspire;

import basemod.BaseMod;
import basemod.devcommands.ConsoleCommand;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import crossspire.ui.CrossSpireHUD;
import crossspire.remote.StageHost;
import crossspire.sync.MessageRouter;
import crossspire.sync.SyncExecutor;
import crossspire.combat.CombatResultReplayer;
import crossspire.combat.CentralQueueManager;
import crossspire.network.StarConnectionManager;
import crossspire.network.Protocol;
import crossspire.network.RoomHost;
import crossspire.network.HeartbeatManager;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.resource.ResourceRegistryTracker;
import crossspire.ui.LobbyState;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import crossspire.ui.CrossSpireCommand;
import crossspire.ui.LobbyScreen;
import crossspire.ui.ServerPicker;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@SpireInitializer
public class CrossSpireMod {

    public static MessageRouter messageRouter;
    public static LobbyScreen lobbyScreen;
    public static CentralQueueManager centralQueueManager;
    public static StarConnectionManager connectionManager;
    public static RoomHost roomHost;
    public static LobbyState lobbyState;
    public static StageHost stageHost;
    public static CrossSpireHUD hud;
    public static String playerId = "";
    public static String hostId = "";
    public static AbstractPlayer localPlayer = null;
    private static final AtomicInteger seqCounter = new AtomicInteger(0);

    public static int nextSeq() {
        return seqCounter.incrementAndGet();
    }

    public static boolean isRoomHost() {
        return ServerPicker.isRoomHost;
    }

    public static String shortId(String id) {
        if (id == null || id.length() < 8) return id != null ? id : "?";
        return id.substring(0, 8);
    }

    public static void initialize() {
        BaseMod.logger.info("CrossSpire mod initialized");
        BaseMod.logger.info("CrossSpire EventSuppression ready, current=" + EventSuppression.isSuppressed());

        centralQueueManager = new CentralQueueManager();
        connectionManager = new StarConnectionManager();
        lobbyState = new LobbyState();
        stageHost = new StageHost("");
        messageRouter = new MessageRouter(new SyncExecutor(), centralQueueManager, new CombatResultReplayer());

        lobbyScreen = new LobbyScreen();
        lobbyScreen.hide();
        ConsoleCommand.addCommand("crossspire", CrossSpireCommand.class);
        hud = new CrossSpireHUD();

        if (ServerPicker.autoConnect) {
            connect();
        }
    }

    public static void runStartupScript() {
        try {
            String stsDir = "/storage/emulated/0/Android/data/io.stamethyst/files/sts";
            java.io.File f = new java.io.File(stsDir + "/crossspire_startup.txt");
            if (f.exists()) {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    BaseMod.logger.info("CrossSpire startup script: " + line);
                    ConsoleCommand.execute(line.split(" "));
                }
                br.close();
                f.delete();
            }
        } catch (Exception e) {
        }
        startBatchWatcher();
    }

    private static void startBatchWatcher() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try { Thread.sleep(10000); } catch (InterruptedException e1) { return; }
                String batchFile = "/storage/emulated/0/Android/data/io.stamethyst/files/sts/crossspire_batch.txt";
                while (true) {
                    try { Thread.sleep(5000); } catch (InterruptedException e) { break; }
                    try {
                        java.io.File f = new java.io.File(batchFile);
                        if (!f.exists()) continue;
                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
                        String line;
                        while ((line = br.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty() || line.startsWith("#")) continue;
                            BaseMod.logger.info("CrossSpire batch: " + line);
                            final String cmd = line;
                            try {
                                com.badlogic.gdx.Gdx.app.postRunnable(new Runnable() {
                                    @Override public void run() {
                                        ConsoleCommand.execute(cmd.split(" "));
                                    }
                                });
                            } catch (Exception e) {
                                ConsoleCommand.execute(cmd.split(" "));
                            }
                        }
                        br.close();
                        f.delete();
                    } catch (Exception ignored) {}
                }
            }
        }, "BatchWatcher").start();
    }

    public static void send(String message) {
        if (connectionManager == null) return;
        if (ServerPicker.isRoomHost) {
            String target = extractTarget(message);
            if (target != null && !target.isEmpty()) {
                connectionManager.send(target, message);
            } else {
                connectionManager.broadcast(message);
            }
        } else {
            connectionManager.send("host", message);
        }
    }

    private static String extractTarget(String message) {
        try {
            JsonObject obj = new JsonParser().parse(message).getAsJsonObject();
            if (obj.has("target")) return obj.get("target").getAsString();
        } catch (Exception ignored) {}
        return null;
    }

    public static void connect() {
        if (playerId.isEmpty()) {
            playerId = UUID.randomUUID().toString();
            stageHost.setLocalPlayerId(playerId);
        }
        BaseMod.logger.info("CrossSpire connect() playerId=" + playerId.substring(0, 8)
            + " isRoomHost=" + ServerPicker.isRoomHost);
        if (ServerPicker.isRoomHost) {
            connectionManager.start();
            connectionManager.setOnPeerConnectedListener(new StarConnectionManager.OnPeerConnectedListener() {
                @Override
                public void onPeerConnected(String peerId) {
                    onPlayerConnected(peerId);
                }
            });
            hostId = playerId;
            roomHost = new RoomHost(playerId);
            roomHost.addPlayer(playerId);
            HeartbeatManager.setOnPeerTimeoutListener(new HeartbeatManager.OnPeerTimeoutListener() {
                @Override
                public void onPeerTimeout(String peerId) {
                    if (roomHost != null) roomHost.onPeerTimeout(peerId);
                }
            });
            lobbyScreen.setStatus("Hosting on :" + connectionManager.getPort());
            HeartbeatManager.start();
            onRoomJoined();
        } else {
            lobbyScreen.setStatus("Connecting to " + ServerPicker.hostIp + ":" + ServerPicker.hostPort);
            connectionManager.connectTo("host", ServerPicker.hostIp, ServerPicker.hostPort);
            HeartbeatManager.setOnPeerTimeoutListener(new HeartbeatManager.OnPeerTimeoutListener() {
                @Override
                public void onPeerTimeout(String peerId) {
                    if ("host".equals(peerId)) {
                        BaseMod.logger.info("CrossSpire host timeout — disconnected");
                        onHostTimeout();
                    }
                }
            });
            HeartbeatManager.start();
        }
    }

    private static void onHostTimeout() {
        HeartbeatManager.stop();
        if (connectionManager != null) {
            connectionManager.stop();
        }
        roomHost = null;
        hostId = "";
        lobbyScreen.setStatus("Host timed out — rejoin with: crossspire join <ip> <port>");
        BaseMod.logger.info("CrossSpire host disconnect complete — waiting for reconnect");
    }

    public static void disconnect() {
        HeartbeatManager.stop();
        if (connectionManager != null) {
            connectionManager.stop();
        }
        roomHost = null;
        lobbyScreen.setStatus("Disconnected");
    }

    public static boolean isConnected() {
        return connectionManager != null && connectionManager.connectionCount() > 0;
    }

    public static void onRoomJoined() {
        lobbyScreen.setStatus("Hosting on :" + connectionManager.getPort());
        ResourceRegistryTracker.sendMyRegistry();
        connectionManager.sendHello();
    }

    public static void onPlayerConnected(String remotePlayerId) {
        BaseMod.logger.info("CrossSpire player connected: " + remotePlayerId.substring(0, 8));
        if (roomHost != null) {
            roomHost.addPlayer(remotePlayerId);
            JsonObject joined = new JsonObject();
            joined.addProperty("type", "player_joined");
            joined.addProperty("playerId", remotePlayerId);
            connectionManager.broadcast(joined.toString());
        }
        RemotePlayerRegistry.register(remotePlayerId);
        lobbyState.onPlayerJoined(remotePlayerId);
        HeartbeatManager.handlePong(remotePlayerId);

        if (roomHost != null) {
            JsonObject roomState = new JsonObject();
            roomState.addProperty("type", "room_state");
            roomState.addProperty("code", ServerPicker.roomCode);
            roomState.addProperty("host", hostId);
            send(roomState.toString());
        }
    }
}
