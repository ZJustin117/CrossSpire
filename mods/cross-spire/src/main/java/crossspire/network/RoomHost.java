package crossspire.network;

import com.google.gson.Gson;
import java.util.ArrayList;
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
}
