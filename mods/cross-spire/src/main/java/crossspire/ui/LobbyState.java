package crossspire.ui;

import basemod.BaseMod;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class LobbyState {

    private final Set<String> readyPlayers = new CopyOnWriteArraySet<String>();
    private final Map<String, String> characterChoices = new ConcurrentHashMap<String, String>();
    private int roomSize = 1;
    private boolean started = false;
    private String pendingReadyCharacter = null;

    public void markLocalReady(String character) {
        readyPlayers.add(CrossSpireMod.playerId);
        characterChoices.put(CrossSpireMod.playerId, character.toUpperCase());

        if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            broadcastAndCheck(character);
        } else {
            pendingReadyCharacter = character.toUpperCase();
            BaseMod.logger.info("LobbyState ready deferred (not connected yet): " + character);
        }
    }

    public void onRoomJoined() {
        roomSize = 1;
        BaseMod.logger.info("LobbyState room joined, size=1");
        flushPending();
    }

    public void onPlayerJoined(String playerId) {
        roomSize++;
        BaseMod.logger.info("LobbyState player_joined: " + playerId.substring(0, 8) + " size=" + roomSize);
        flushPending();
        checkAllReady();
    }

    public void onPlayerReady(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        String character = msg.has("character") ? msg.get("character").getAsString() : "IRONCLAD";

        if (source.isEmpty() || source.equals(CrossSpireMod.playerId)) return;

        readyPlayers.add(source);
        characterChoices.put(source, character.toUpperCase());

        BaseMod.logger.info("LobbyState " + source.substring(0, 8) + " ready as " + character
            + " ready=" + readyPlayers.size() + "/" + roomSize);

        checkAllReady();
    }

    private void flushPending() {
        if (pendingReadyCharacter != null && CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            String c = pendingReadyCharacter;
            pendingReadyCharacter = null;
            broadcastAndCheck(c);
        }
    }

    private void broadcastAndCheck(String character) {
        Protocol.PlayerReady ready = new Protocol.PlayerReady();
        ready.source = CrossSpireMod.playerId;
        ready.seq = 1;
        ready.character = character.toUpperCase();

        CrossSpireMod.relayClient.send(Protocol.GSON.toJson(ready));
        BaseMod.logger.info("LobbyState sent player_ready as " + character
            + " ready=" + readyPlayers.size() + "/" + roomSize);

        checkAllReady();
    }

    private void checkAllReady() {
        if (started) return;
        if (roomSize < 2) return;
        if (readyPlayers.size() < roomSize) return;

        started = true;
        BaseMod.logger.info("LobbyState ALL READY! Starting game...");

        String myChar = characterChoices.get(CrossSpireMod.playerId);
        if (myChar == null) myChar = "IRONCLAD";

        String seed = String.valueOf(System.currentTimeMillis() % 900000 + 100000);
        Protocol.StageSync sync = new Protocol.StageSync();
        sync.character = myChar;
        sync.seed = seed;
        sync.source = CrossSpireMod.playerId;
        sync.seq = 1;
        sync.act = 1;

        if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            CrossSpireMod.relayClient.send(Protocol.GSON.toJson(sync));
            BaseMod.logger.info("LobbyState broadcast stage_sync: " + myChar + " seed=" + seed);
        }

        String usedSeed = crossspire.remote.GameStarter.start(myChar, seed);
        if (usedSeed != null) {
            CrossSpireMod.lastStartedChar = myChar;
            CrossSpireMod.lastStartedSeed = usedSeed;
            CrossSpireMod.startedGame = true;
        }
    }
}
