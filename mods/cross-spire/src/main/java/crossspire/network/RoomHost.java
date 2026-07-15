package crossspire.network;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RoomHost {

    private final String hostPlayerId;
    private final List<String> playerIds = new CopyOnWriteArrayList<String>();

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
}
