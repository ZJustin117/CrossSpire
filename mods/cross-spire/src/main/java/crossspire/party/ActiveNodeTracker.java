package crossspire.party;

/**
 * Local diagnostic + gate state for the party's active node instance (T7.7c).
 * Not the RoomHost directory; each client tracks the last opened instance for status/UI.
 */
public final class ActiveNodeTracker {

    private static volatile String partyId = "";
    private static volatile String mapInstanceId = "";
    private static volatile String nodeId = "";
    private static volatile String nodeInstanceId = "";
    private static volatile String encounter = "";
    private static volatile String roomType = "";
    private static volatile boolean rewardPhaseActive = false;

    private ActiveNodeTracker() {}

    public static synchronized void clear() {
        partyId = "";
        mapInstanceId = "";
        nodeId = "";
        nodeInstanceId = "";
        encounter = "";
        roomType = "";
        rewardPhaseActive = false;
    }

    public static synchronized void setActive(String party, String mapId, String nId,
                                             String instanceId, String room, String enc) {
        partyId = party != null ? party : "";
        mapInstanceId = mapId != null ? mapId : "";
        nodeId = nId != null ? nId : "";
        nodeInstanceId = instanceId != null ? instanceId : "";
        roomType = room != null ? room : "";
        encounter = enc != null ? enc : "";
        rewardPhaseActive = false;
    }

    public static synchronized void setRewardPhaseActive(boolean active) {
        rewardPhaseActive = active;
    }

    public static String getPartyId() { return partyId; }
    public static String getMapInstanceId() { return mapInstanceId; }
    public static String getNodeId() { return nodeId; }
    public static String getNodeInstanceId() { return nodeInstanceId; }
    public static String getEncounter() { return encounter; }
    public static String getRoomType() { return roomType; }
    public static boolean isRewardPhaseActive() { return rewardPhaseActive; }

    public static String summary() {
        return "nodeInstance=" + nodeInstanceId
            + " node=" + nodeId
            + " type=" + roomType
            + " encounter=" + encounter
            + " map=" + mapInstanceId
            + " rewardPhase=" + rewardPhaseActive;
    }

    /** Force-follow: forget prior instance before installing a new open. */
    public static synchronized void forceLeave(String reason) {
        if (nodeInstanceId != null && !nodeInstanceId.isEmpty()) {
            // Keep last id only in logs; clear identity so play/shop cannot target old room.
            basemod.BaseMod.logger.info("ActiveNodeTracker forceLeave from=" + nodeInstanceId
                + " reason=" + reason);
        }
        clear();
    }
}
