package crossspire.network;

import basemod.BaseMod;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.resource.ResourceRegistryTracker;
import crossspire.ui.ServerPicker;
import java.net.URI;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class RelayClient extends WebSocketClient {

    public RelayClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        BaseMod.logger.info("CrossSpire connected to relay server: " + getURI());
    }

    @Override
    public void onMessage(String message) {
        JsonObject msg = Protocol.GSON.fromJson(message, JsonObject.class);
        String type = msg.has("type") ? msg.get("type").getAsString() : "";

        if ("connected".equals(type)) {
            String playerId = msg.get("playerId").getAsString();
            CrossSpireMod.playerId = playerId;
            BaseMod.logger.info("CrossSpire assigned playerId: " + playerId);
            CrossSpireMod.lobbyScreen.setStatus("Connected as " + playerId.substring(0, 8));
            joinRoom();
        } else if ("room_state".equals(type)) {
            CrossSpireMod.lobbyScreen.setStatus("In room " + msg.get("code").getAsString());
            CrossSpireMod.lobbyState.onRoomJoined();
            CrossSpireMod.p2pManager.start();
            CrossSpireMod.p2pManager.sendHello();
            ResourceRegistryTracker.sendMyRegistry();
        } else if ("player_joined".equals(type)) {
            String joinedId = msg.get("playerId").getAsString();
            BaseMod.logger.info("CrossSpire player joined: " + joinedId);
            RemotePlayerRegistry.register(joinedId);
            CrossSpireMod.lobbyState.onPlayerJoined(joinedId);
            // Re-broadcast battle_start if we already started
            if (CrossSpireMod.startedGame) {
                CrossSpireMod.resendBattleStart();
            }
        } else {
            BaseMod.logger.info("CrossSpire routing: type=" + type + " len=" + message.length());
            CrossSpireMod.messageRouter.route(message);
        }
    }

    private void joinRoom() {
        String roomCode = ServerPicker.roomCode;
        JsonObject join = new JsonObject();
        join.addProperty("type", "join");
        join.addProperty("code", roomCode);
        send(join.toString());
        BaseMod.logger.info("CrossSpire joined room: " + roomCode);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        BaseMod.logger.info("CrossSpire disconnected: " + code + " " + reason);
    }

    @Override
    public void onError(Exception ex) {
        BaseMod.logger.info("CrossSpire connection error: " + ex.getMessage());
    }
}
