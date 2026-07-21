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
        if (!sourcePlayerId.equals(allocated.nodeInstanceHostId)) return false;
        if (!sourcePlayerId.equals(party.nodeInstanceHostId)) return false;
        if (commit.generationResult == null || commit.generationResult.encounter == null
            || commit.generationResult.encounter.isEmpty()) {
            return false;
        }
        if (openedInstances.contains(allocated.nodeInstanceId)) return false;
        openedInstances.add(allocated.nodeInstanceId);
        return true;
    }

    public synchronized boolean isOpened(String nodeInstanceId) {
        return nodeInstanceId != null && openedInstances.contains(nodeInstanceId);
    }
}
