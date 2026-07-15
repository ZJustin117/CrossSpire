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
    private String myCharacter = "IRONCLAD";

    public String getMyCharacter() { return myCharacter; }

    private static String safeSub(String s) {
        return s == null ? "?" : s.length() >= 8 ? s.substring(0, 8) : s;
    }

    public void markLocalReady(String character) {
        myCharacter = character.toUpperCase();

        if (CrossSpireMod.isConnected()) {
            readyPlayers.add(CrossSpireMod.playerId);
            characterChoices.put(CrossSpireMod.playerId, myCharacter);
            broadcastAndCheck(character);
        } else {
            pendingReadyCharacter = character.toUpperCase();
            BaseMod.logger.info("LobbyState ready deferred (not connected yet): " + character);
        }
    }

    public void onRoomJoined(int size) {
        roomSize = Math.max(1, size);
        BaseMod.logger.info("LobbyState room joined, size=" + roomSize);
        flushPending();
    }

    public void onPlayerJoined(String playerId) {
        roomSize++;
        BaseMod.logger.info("LobbyState player_joined: " + safeSub(playerId) + " size=" + roomSize);
        flushPending();
        resendOwnReady();
        checkAllReady();
    }

    public void onPlayerLeft(String playerId) {
        roomSize = Math.max(1, roomSize - 1);
        readyPlayers.remove(playerId);
        characterChoices.remove(playerId);
        BaseMod.logger.info("LobbyState player_left: " + safeSub(playerId) + " size=" + roomSize);
    }

    private void resendOwnReady() {
        if (CrossSpireMod.playerId.isEmpty()) return;
        if (!readyPlayers.contains(CrossSpireMod.playerId)) return;
        if (!CrossSpireMod.isConnected()) return;

        String character = characterChoices.get(CrossSpireMod.playerId);
        if (character == null) character = "IRONCLAD";

        Protocol.PlayerReady ready = new Protocol.PlayerReady();
        ready.source = CrossSpireMod.playerId;
        ready.seq = 1;
        ready.character = character;

        CrossSpireMod.send(Protocol.GSON.toJson(ready));
        BaseMod.logger.info("LobbyState resent player_ready as " + character);
    }

    public void onPlayerReady(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        String character = msg.has("character") ? msg.get("character").getAsString() : "IRONCLAD";

        if (source.isEmpty() || source.equals(CrossSpireMod.playerId)) return;

        readyPlayers.add(source);
        characterChoices.put(source, character.toUpperCase());

        BaseMod.logger.info("LobbyState " + safeSub(source) + " ready as " + character
            + " ready=" + readyPlayers.size() + "/" + roomSize);

        checkAllReady();
    }

    private void flushPending() {
        if (pendingReadyCharacter != null && CrossSpireMod.isConnected() && !CrossSpireMod.playerId.isEmpty()) {
            String c = pendingReadyCharacter;
            pendingReadyCharacter = null;
            readyPlayers.add(CrossSpireMod.playerId);
            characterChoices.put(CrossSpireMod.playerId, c);
            broadcastAndCheck(c);
        }
    }

    private void broadcastAndCheck(String character) {
        Protocol.PlayerReady ready = new Protocol.PlayerReady();
        ready.source = CrossSpireMod.playerId;
        ready.seq = 1;
        ready.character = character.toUpperCase();

        CrossSpireMod.send(Protocol.GSON.toJson(ready));
        BaseMod.logger.info("LobbyState sent player_ready as " + character
            + " ready=" + readyPlayers.size() + "/" + roomSize);

        checkAllReady();
    }

    private void checkAllReady() {
        if (started) return;
        if (roomSize < 2) return;
        if (readyPlayers.size() < roomSize) return;

        started = true;
        BaseMod.logger.info("LobbyState ALL READY! ready=" + readyPlayers.size());

        String hostId = CrossSpireMod.stageHost.electHost(
            readyPlayers.toArray(new String[0])
        );

        CrossSpireMod.stageHost.setStageHost(hostId);

        BaseMod.logger.info("LobbyState host=" + safeSub(hostId) + " (self=" + safeSub(CrossSpireMod.playerId) + ")");

        if (hostId.equals(CrossSpireMod.playerId)) {
            String seed = String.valueOf(System.currentTimeMillis() % 900000 + 100000);
            Protocol.StageSync sync = new Protocol.StageSync();
            sync.character = myCharacter;
            sync.seed = seed;
            sync.source = CrossSpireMod.playerId;
            sync.seq = 1;
            sync.act = 1;

            if (CrossSpireMod.isConnected()) {
                CrossSpireMod.send(Protocol.GSON.toJson(sync));
            }
            BaseMod.logger.info("LobbyState host broadcast seed=" + seed);
            com.megacrit.cardcrawl.helpers.SeedHelper.setSeed(seed);
        }
    }
}
