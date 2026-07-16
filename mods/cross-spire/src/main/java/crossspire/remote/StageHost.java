package crossspire.remote;

import java.util.Arrays;
import java.util.Random;

public class StageHost {

    private String localPlayerId;
    private String hostPlayerId;
    private final Random stageRng = new Random();

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

    public Random getStageRng() {
        return stageRng;
    }

    public static String electHost(String[] playerIds) {
        String[] sorted = playerIds.clone();
        Arrays.sort(sorted);
        return sorted[0];
    }

    public boolean canOwnLocally(String resourceType, String resourceId) {
        if ("card".equals(resourceType)) {
            return true;
        }
        return isStageHost();
    }
}
