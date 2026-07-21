package crossspire.map;

import crossspire.network.Protocol;
import crossspire.party.PartyState;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure authorization for node generation commit and open.
 * RoomHost accepts one commit per node_instance_id from the elected NIH.
 */
public final class NodeOpenCoordinator {

    private final Set<String> openedInstances = new HashSet<String>();

    public synchronized boolean acceptCommit(PartyState party, String sourcePlayerId,
                                             Protocol.NodeInstanceInfo allocated,
                                             Protocol.NodeGenerationCommitPayload commit) {
        if (party == null || sourcePlayerId == null || allocated == null || commit == null) return false;
        if (commit.nodeInstanceId == null || !commit.nodeInstanceId.equals(allocated.nodeInstanceId)) return false;
        if (commit.partyId == null || !commit.partyId.equals(party.partyId)) return false;
        if (commit.mapInstanceId == null || !commit.mapInstanceId.equals(party.mapInstanceId)) return false;
        if (commit.nodeId == null || !commit.nodeId.equals(allocated.nodeId)) return false;
        if (commit.generationResult == null || allocated.roomType == null
            || !allocated.roomType.equals(commit.generationResult.roomType)) return false;
        if (!sourcePlayerId.equals(allocated.nodeInstanceHostId)) return false;
        if (!sourcePlayerId.equals(party.nodeInstanceHostId)) return false;
        if (!validGenerationResult(commit.generationResult, allocated.nodeId)) return false;
        if (openedInstances.contains(allocated.nodeInstanceId)) return false;
        openedInstances.add(allocated.nodeInstanceId);
        return true;
    }

    public synchronized boolean isOpened(String nodeInstanceId) {
        return nodeInstanceId != null && openedInstances.contains(nodeInstanceId);
    }

    private static boolean validGenerationResult(Protocol.NodeGenerationResult result, String nodeId) {
        if (result == null || result.roomType == null || !nodeId.equals(result.nodeId)) return false;
        if ("monster".equals(result.roomType)) {
            return result.encounter != null && !result.encounter.isEmpty();
        }
        if ("event".equals(result.roomType)) {
            return result.eventInterface != null
                && result.eventInterface.eventInstanceId != null
                && !result.eventInterface.eventInstanceId.isEmpty()
                && result.eventInterface.options != null
                && result.eventInterface.options.length > 0;
        }
        return false;
    }
}
