package crossspire.network;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class RoomHost {

    private final String hostPlayerId;
    private final List<String> playerIds = new CopyOnWriteArrayList<String>();
    private final Map<String, Integer> playerPins = new HashMap<String, Integer>();

    public RoomHost(String hostPlayerId) {
        this.hostPlayerId = hostPlayerId;
    }

    public String getHostPlayerId() {
        return hostPlayerId;
    }

    public List<String> getPlayerIds() {
        return new ArrayList<>(playerIds);
    }

    /**
     * Determines the new room host from remaining connected players.
     * Lexicographically sorts all remaining player IDs and returns the first one.
     * Used when the current host disconnects — all remaining clients compute
     * this locally and arrive at the same deterministic result.
     *
     * @return lexicographically lowest player ID, or null if no players remain
     */
    public String electNewHost() {
        if (playerIds.isEmpty()) return null;
        String[] sorted = playerIds.toArray(new String[0]);
        Arrays.sort(sorted);
        return sorted[0];
    }

    public int getPlayerCount() {
        return playerIds.size();
    }

    public void addPlayer(String playerId) {
        if (!playerIds.contains(playerId)) {
            playerIds.add(playerId);
        }
    }

    public void removePlayer(String playerId) {
        playerIds.remove(playerId);
        playerPins.remove(playerId);
    }

    public boolean hasPlayer(String playerId) {
        return playerIds.contains(playerId);
    }

    public List<String> getOtherPlayers(String senderId) {
        List<String> others = new ArrayList<String>();
        for (String pid : playerIds) {
            if (!pid.equals(senderId)) {
                others.add(pid);
            }
        }
        return others;
    }

    public void pinRoom(String playerId, int roomIndex) {
        playerPins.put(playerId, roomIndex);
    }

    public int checkConsensus() {
        if (playerPins.isEmpty() || playerPins.size() != playerIds.size()) {
            return -1;
        }
        int first = playerPins.values().iterator().next();
        for (int v : playerPins.values()) {
            if (v != first) return -1;
        }
        return first;
    }

    public String getPinsJson() {
        return new Gson().toJson(playerPins);
    }

    /**
     * Called when HeartbeatManager detects a peer timeout.
     * Removes the player and broadcasts player_left.
     * If the timed-out player was the room host, triggers host re-election.
     */
    public void onPeerTimeout(String peerId) {
        removePlayer(peerId);
        basemod.BaseMod.logger.info("RoomHost onPeerTimeout: " + peerId.substring(0, 8)
            + " remaining=" + playerIds.size());

        com.google.gson.JsonObject left = new com.google.gson.JsonObject();
        left.addProperty("type", "player_left");
        left.addProperty("playerId", peerId);
        crossspire.CrossSpireMod.send(left.toString());

        if (peerId.equals(hostPlayerId)) {
            String newHost = electNewHost();
            if (newHost != null && !playerIds.isEmpty()) {
                basemod.BaseMod.logger.info("RoomHost host died — new host=" + newHost.substring(0, 8));
                com.google.gson.JsonObject migration = new com.google.gson.JsonObject();
                migration.addProperty("type", "host_migration");
                migration.addProperty("new_host", newHost);
                crossspire.CrossSpireMod.send(migration.toString());
            }
        }
    }
}
