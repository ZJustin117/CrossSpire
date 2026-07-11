package crossspire;

import basemod.BaseMod;
import basemod.devcommands.ConsoleCommand;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import crossspire.network.RelayClient;
import crossspire.remote.RemoteRenderer;
import crossspire.remote.StageHost;
import crossspire.sync.InvokeExecutor;
import crossspire.sync.MessageRouter;
import crossspire.sync.SyncExecutor;
import crossspire.ui.CrossSpireCommand;
import crossspire.ui.LobbyScreen;
import crossspire.ui.ServerPicker;
import com.google.gson.JsonObject;
import java.net.URI;

@SpireInitializer
public class CrossSpireMod {

    public static RelayClient relayClient;
    public static MessageRouter messageRouter;
    public static LobbyScreen lobbyScreen;
    public static StageHost stageHost;
    public static String playerId = "";
    public static boolean startedGame = false;
    public static String lastStartedChar = "IRONCLAD";
    public static String lastStartedSeed = "";

    public static void initialize() {
        BaseMod.logger.info("CrossSpire mod initialized");
        BaseMod.logger.info("CrossSpire EventSuppression ready, current value=" + EventSuppression.SUPPRESSION.get());

        stageHost = new StageHost();
        messageRouter = new MessageRouter(new InvokeExecutor(), new SyncExecutor(), stageHost);
        lobbyScreen = new LobbyScreen();
        lobbyScreen.hide();
        ConsoleCommand.addCommand("crossspire", CrossSpireCommand.class);
        new RemoteRenderer();

        if (ServerPicker.autoConnect) {
            connect();
        }
    }

    public static void runStartupScript() {
        try {
            String stsDir = "/storage/emulated/0/Android/data/io.stamethyst/files/sts";
            java.io.File f = new java.io.File(stsDir + "/crossspire_startup.txt");
            if (!f.exists()) return;
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
        } catch (Exception e) {
            // ignore — script is optional
        }
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

    public static void resendBattleStart() {
        if (relayClient == null || !relayClient.isOpen() || !startedGame) return;
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "state_sync");
        msg.addProperty("subtype", "battle_start");
        msg.addProperty("source", playerId);
        msg.addProperty("seq", 1);
        msg.addProperty("character", lastStartedChar);
        msg.addProperty("seed", lastStartedSeed);
        relayClient.send(msg.toString());
        BaseMod.logger.info("CrossSpire resend battle_start: " + lastStartedChar + " seed=" + lastStartedSeed);
    }
}
