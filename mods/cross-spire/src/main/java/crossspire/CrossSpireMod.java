package crossspire;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import crossspire.network.RelayClient;
import crossspire.remote.RemoteRenderer;
import crossspire.remote.StageHost;
import crossspire.sync.InvokeExecutor;
import crossspire.sync.MessageRouter;
import crossspire.sync.SyncExecutor;
import crossspire.ui.LobbyScreen;
import crossspire.ui.ServerPicker;
import java.net.URI;

@SpireInitializer
public class CrossSpireMod {

    public static RelayClient relayClient;
    public static MessageRouter messageRouter;
    public static LobbyScreen lobbyScreen;
    public static StageHost stageHost;
    public static String playerId = "";

    public static void initialize() {
        BaseMod.logger.info("CrossSpire mod initialized");
        BaseMod.logger.info("CrossSpire EventSuppression ready, current value=" + EventSuppression.SUPPRESSION.get());

        stageHost = new StageHost();
        messageRouter = new MessageRouter(new InvokeExecutor(), new SyncExecutor(), stageHost);
        lobbyScreen = new LobbyScreen();
        lobbyScreen.show();
        new RemoteRenderer();

        if (ServerPicker.autoConnect) {
            connect();
        }
    }

    public static void connect() {
        lobbyScreen.setStatus("Connecting...");
        try {
            relayClient = new RelayClient(new URI(ServerPicker.serverUrl));
            relayClient.connect();
            BaseMod.logger.info("CrossSpire connecting to relay: " + ServerPicker.serverUrl);
        } catch (Exception e) {
            BaseMod.logger.error("CrossSpire relay connection failed: " + e.getMessage());
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
