package crossspire;

import basemod.BaseMod;
import basemod.devcommands.ConsoleCommand;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import crossspire.network.RelayClient;
import crossspire.remote.RemoteRenderer;
import crossspire.remote.StageHost;
import crossspire.sync.MessageRouter;
import crossspire.sync.SyncExecutor;
import crossspire.combat.CombatResultReplayer;
import crossspire.combat.QueueManager;
import crossspire.network.P2PManager;
import crossspire.network.Protocol;
import crossspire.rng.RngManager;
import crossspire.ui.LobbyState;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import crossspire.ui.CrossSpireCommand;
import crossspire.ui.LobbyScreen;
import crossspire.ui.RemoteStatsOverlay;
import crossspire.ui.RoomPanel;
import crossspire.ui.ServerPicker;
import com.google.gson.JsonObject;
import java.net.URI;

@SpireInitializer
public class CrossSpireMod {

    public static RelayClient relayClient;
    public static MessageRouter messageRouter;
    public static LobbyScreen lobbyScreen;
    public static QueueManager queueManager;
    public static P2PManager p2pManager;
    public static LobbyState lobbyState;
    public static StageHost stageHost;
    public static RngManager rngManager;
    public static String playerId = "";
    public static String syncedSeed = null;
    public static AbstractPlayer localPlayer = null;

    public static void initialize() {
        BaseMod.logger.info("CrossSpire mod initialized");
        BaseMod.logger.info("CrossSpire EventSuppression ready, current value=" + EventSuppression.SUPPRESSION.get());

        queueManager = new QueueManager();
        p2pManager = new P2PManager();
        lobbyState = new LobbyState();
        stageHost = new StageHost("");
        messageRouter = new MessageRouter(new SyncExecutor(), queueManager, new CombatResultReplayer());
        lobbyScreen = new LobbyScreen();
        lobbyScreen.hide();
        ConsoleCommand.addCommand("crossspire", CrossSpireCommand.class);
        new RemoteRenderer();
        new RemoteStatsOverlay();
        new RoomPanel();

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
            // ignore — script is optional
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

    public static void connect() {
        BaseMod.logger.info("CrossSpire connect() called, url=" + ServerPicker.serverUrl);
        lobbyScreen.setStatus("Connecting...");
        try {
            relayClient = new RelayClient(new URI(ServerPicker.serverUrl));
            relayClient.connect();
            BaseMod.logger.info("CrossSpire connecting to relay: " + ServerPicker.serverUrl);
        } catch (Exception e) {
            BaseMod.logger.error("CrossSpire relay connection failed: " + e.getMessage());
            BaseMod.logger.error("CrossSpire relay error stack: " + java.util.Arrays.toString(e.getStackTrace()));
            lobbyScreen.setStatus("Error: " + e.getMessage());
        }
    }

    public static void disconnect() {
        if (relayClient != null) {
            relayClient.close();
            relayClient = null;
        }
        lobbyScreen.setStatus("Disconnected");
    }

    public static boolean isConnected() {
        return relayClient != null && relayClient.isOpen();
    }
}
