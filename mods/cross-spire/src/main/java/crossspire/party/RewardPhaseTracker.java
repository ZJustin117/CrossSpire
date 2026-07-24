package crossspire.party;

import java.util.HashSet;
import java.util.Set;

/** Pure aggregation for reward_done until all party members complete (T7.8). */
public final class RewardPhaseTracker {

    private final Set<String> donePlayers = new HashSet<String>();
    private final Set<String> completedNodeInstances = new HashSet<String>();
    private String partyId = "";
    private String nodeInstanceId = "";
    private boolean active = false;

    /**
     * Begin reward phase for a node. No-op if this node already completed
     * (late dual endBattle / enter must not re-block room_pin).
     * @return false if ignored
     */
    public synchronized boolean enter(String partyId, String nodeInstanceId) {
        String node = nodeInstanceId != null ? nodeInstanceId : "";
        if (!node.isEmpty() && completedNodeInstances.contains(node)) {
            return false;
        }
        // Already tracking same active node: keep done set.
        if (active && this.nodeInstanceId.equals(node) && !node.isEmpty()) {
            return true;
        }
        this.partyId = partyId != null ? partyId : "";
        this.nodeInstanceId = node;
        this.donePlayers.clear();
        this.active = true;
        return true;
    }

    public synchronized void clear() {
        if (active && nodeInstanceId != null && !nodeInstanceId.isEmpty()) {
            completedNodeInstances.add(nodeInstanceId);
        }
        donePlayers.clear();
        partyId = "";
        nodeInstanceId = "";
        active = false;
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized boolean isCompletedNode(String nodeInstanceId) {
        return nodeInstanceId != null && completedNodeInstances.contains(nodeInstanceId);
    }

    public synchronized String getPartyId() {
        return partyId;
    }

    public synchronized String getNodeInstanceId() {
        return nodeInstanceId;
    }

    /**
     * @return true if all members have reported done
     * Late done after clear for a completed node returns false without re-activating.
     */
    public synchronized boolean markDone(String playerId, PartyState party) {
        if (playerId == null || party == null) return false;
        if (!party.memberIds.contains(playerId)) return false;
        if (!active) return false;
        if (nodeInstanceId.isEmpty()) return false;
        donePlayers.add(playerId);
        return donePlayers.containsAll(party.memberIds);
    }

    public synchronized int doneCount() {
        return donePlayers.size();
    }

    public synchronized boolean blocksRoomPin() {
        return active;
    }
}
