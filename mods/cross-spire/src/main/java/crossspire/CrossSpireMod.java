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
import crossspire.combat.PartyEndTurnTracker;
import crossspire.network.StarConnectionManager;
import crossspire.network.Protocol;
import crossspire.network.RoomHost;
import crossspire.network.HeartbeatManager;
import crossspire.network.StandardPacket;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.resource.ResourceRegistryTracker;
import crossspire.ui.LobbyState;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import crossspire.ui.CrossSpireCommand;
import crossspire.ui.LobbyScreen;
import crossspire.ui.ServerPicker;
import crossspire.party.PartyManager;
import crossspire.party.PartySnapshotSender;
import crossspire.party.PartyState;
import crossspire.party.RewardPhaseTracker;
import crossspire.party.RoomNavigationGate;
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
    public static PartyManager partyManager;
    public static PartyEndTurnTracker partyEndTurnTracker;
    public static RewardPhaseTracker rewardPhaseTracker;
    public static RoomNavigationGate roomNavigationGate;
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
        connectionManager = null;
        lobbyState = new LobbyState();
        stageHost = new StageHost("");
        partyManager = new PartyManager();
        partyEndTurnTracker = new PartyEndTurnTracker();
        rewardPhaseTracker = new RewardPhaseTracker();
        roomNavigationGate = new RoomNavigationGate();
        messageRouter = new MessageRouter(new SyncExecutor(), centralQueueManager, new CombatResultReplayer());

        lobbyScreen = new LobbyScreen();
        lobbyScreen.hide();
        ConsoleCommand.addCommand("crossspire", CrossSpireCommand.class);
        hud = new CrossSpireHUD();

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

    public static void send(StandardPacket pkt) {
        send(StandardPacket.toJson(pkt));
    }

    /** Routes a gameplay message only to members of its party; clients always relay through RoomHost. */
    public static void sendToParty(String partyId, String message) {
        if (connectionManager == null) return;
        if (!isRoomHost()) {
            connectionManager.send("host", message);
            return;
        }
        PartyState party = partyManager != null ? partyManager.getParty(partyId) : null;
        if (party == null) return;
        for (String memberId : party.memberIds) {
            if (!memberId.equals(playerId)) connectionManager.send(memberId, message);
        }
    }

    /** RoomHost-only point-to-point forwarding for a party coordinator or object owner. */
    public static void sendToPlayer(String playerId, String message) {
        if (connectionManager == null) return;
        if (isRoomHost()) connectionManager.send(playerId, message);
        else connectionManager.send("host", message);
    }

    private static String extractTarget(String message) {
        try {
            JsonObject obj = new JsonParser().parse(message).getAsJsonObject();
            if (obj.has("target")) return obj.get("target").getAsString();
        } catch (Exception ignored) {}
        return null;
    }

    public static void host(String advertisedIp, int port) {
        resetConnectionManager(new StarConnectionManager(port, advertisedIp));
        ServerPicker.isRoomHost = true;
        ensurePlayerId();
        BaseMod.logger.info("CrossSpire host() playerId=" + playerId.substring(0, 8));
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
        partyManager.initializeDefaultParty(roomHost.getPlayerIds(), "");
        HeartbeatManager.setOnPeerTimeoutListener(new HeartbeatManager.OnPeerTimeoutListener() {
            @Override
            public void onPeerTimeout(String peerId) {
                if (roomHost != null) roomHost.onPeerTimeout(peerId);
                if (partyManager != null) {
                    partyManager.removePlayer(peerId);
                    send(PartySnapshotSender.build());
                }
            }
        });
        lobbyScreen.setStatus("Hosting on " + advertisedIp + ":" + port);
        HeartbeatManager.start();
        onRoomJoined();
    }

    public static void join(String host, int port) {
        resetConnectionManager(new StarConnectionManager(port, host));
        ServerPicker.isRoomHost = false;
        ensurePlayerId();
        BaseMod.logger.info("CrossSpire join() playerId=" + playerId.substring(0, 8));
        lobbyScreen.setStatus("Connecting to " + host + ":" + port);
        connectionManager.connectTo("host", host, port);
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

    private static void ensurePlayerId() {
        if (playerId.isEmpty()) {
            playerId = UUID.randomUUID().toString();
            stageHost.setLocalPlayerId(playerId);
        }
    }

    private static void resetConnectionManager(StarConnectionManager next) {
        HeartbeatManager.stop();
        if (connectionManager != null) {
            connectionManager.stop();
        }
        connectionManager = next;
        roomHost = null;
        hostId = "";
    }

    private static void onHostTimeout() {
        HeartbeatManager.stop();
        if (connectionManager != null) {
            connectionManager.stop();
        }
        connectionManager = null;
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
        connectionManager = null;
        roomHost = null;
        hostId = "";
        lobbyScreen.setStatus("Disconnected");
    }

    public static boolean isConnected() {
        return connectionManager != null && connectionManager.connectionCount() > 0;
    }

    public static void onRoomJoined() {
        lobbyScreen.setStatus("Hosting on " + connectionManager.getAdvertisedIp() + ":" + connectionManager.getPort());
        ResourceRegistryTracker.sendMyRegistry();
        connectionManager.sendHello();
    }

    public static void onPlayerConnected(String remotePlayerId) {
        BaseMod.logger.info("CrossSpire player connected: " + remotePlayerId.substring(0, 8));
        if (roomHost != null) {
            roomHost.addPlayer(remotePlayerId);
            partyManager.addPlayerToDefaultParty(remotePlayerId);
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
            // room_state establishes hostId before peers authorize party_snapshot.
            send(PartySnapshotSender.build());
        }
    }
}
