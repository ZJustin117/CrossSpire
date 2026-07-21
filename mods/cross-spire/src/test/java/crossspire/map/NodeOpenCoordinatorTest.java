package crossspire.map;

import crossspire.network.Protocol;
import crossspire.party.PartyManager;
import crossspire.party.PartyState;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NodeOpenCoordinatorTest {

    @Test
    public void acceptsFirstCommitFromElectedNihOnly() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob"), "");
        parties.bindMap("P0", "M1", "start", "EXORDIUM");
        parties.setNodeInstanceHost("P0", "alice");
        PartyState party = parties.getParty("P0");

        Protocol.NodeInstanceInfo allocated = info("node:M1/P0/a/1", "alice");
        Protocol.NodeGenerationCommitPayload commit = commit(allocated, "Cultist");
        NodeOpenCoordinator coordinator = new NodeOpenCoordinator();

        assertFalse(coordinator.acceptCommit(party, "bob", allocated, commit));
        assertTrue(coordinator.acceptCommit(party, "alice", allocated, commit));
        assertFalse(coordinator.acceptCommit(party, "alice", allocated, commit));
        assertTrue(coordinator.isOpened(allocated.nodeInstanceId));
    }

    private static Protocol.NodeInstanceInfo info(String id, String nih) {
        Protocol.NodeInstanceInfo info = new Protocol.NodeInstanceInfo();
        info.nodeInstanceId = id;
        info.mapInstanceId = "M1";
        info.partyId = "P0";
        info.nodeId = "a";
        info.visitId = 1;
        info.nodeInstanceHostId = nih;
        info.status = "allocated";
        return info;
    }

    private static Protocol.NodeGenerationCommitPayload commit(Protocol.NodeInstanceInfo info, String encounter) {
        Protocol.NodeGenerationCommitPayload commit = new Protocol.NodeGenerationCommitPayload();
        commit.nodeInstanceId = info.nodeInstanceId;
        commit.partyId = info.partyId;
        commit.mapInstanceId = info.mapInstanceId;
        commit.nodeId = info.nodeId;
        commit.generationRevision = 1;
        commit.generationResult = NodeInstanceOpenedSender.defaultMonsterResult(info.nodeId);
        commit.generationResult.encounter = encounter;
        return commit;
    }
}
