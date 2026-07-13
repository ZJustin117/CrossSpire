package crossspire.remote;

import java.util.Arrays;

public class StageHost {

    private String localPlayerId;
    private String hostPlayerId;

    public StageHost(String localPlayerId) {
        this.localPlayerId = localPlayerId;
    }

    public void setLocalPlayerId(String playerId) {
        this.localPlayerId = playerId;
    }

    public void setStageHost(String hostId) {
        this.hostPlayerId = hostId;
    }

    public String getStageHostId() {
        return hostPlayerId;
    }

    public boolean isStageHost() {
        return hostPlayerId != null && hostPlayerId.equals(localPlayerId);
    }

    public static String electHost(String[] playerIds) {
        Arrays.sort(playerIds);
        return playerIds[0];
    }

    public boolean canOwnLocally(String resourceType, String resourceId) {
        if ("card".equals(resourceType)) {
            return true;
        }
        return isStageHost();
    }
}
