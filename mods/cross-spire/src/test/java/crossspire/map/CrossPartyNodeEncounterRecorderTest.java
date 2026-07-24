package crossspire.map;

import crossspire.party.PartyManager;
import crossspire.party.PartyState;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CrossPartyNodeEncounterRecorderTest {

    @Test
    public void recordsWhenTwoPartiesAllocateSameMapNodeWithoutMergingInstances() {
        MapDefinition map = map();
        MapRegistry maps = new MapRegistry();
        maps.register("alice", map);
        NodeInstanceRegistry nodes = new NodeInstanceRegistry();
        CrossPartyNodeEncounterRecorder recorder = new CrossPartyNodeEncounterRecorder();
        NodeEntryCoordinator coordinator = new NodeEntryCoordinator(maps, nodes, recorder);

        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob"), "");
        parties.bindMap("P0", "M1", "start", "EXORDIUM");
        parties.setNodeInstanceHost("P0", "alice");
        PartyState p0 = parties.getParty("P0");

        parties.leave("bob");
        String p1Id = parties.getPartyIdForPlayer("bob");
        parties.bindMap(p1Id, "M1", "start", "EXORDIUM");
        parties.setNodeInstanceHost(p1Id, "bob");
        PartyState p1 = parties.getParty(p1Id);

        NodeInstance a = coordinator.allocateOnConsensus(p0, "a", 1);
        assertNotNull(a);
        assertEquals(0, recorder.size());

        NodeInstance b = coordinator.allocateOnConsensus(p1, "a", 1);
        assertNotNull(b);
        assertTrue(!a.nodeInstanceId.equals(b.nodeInstanceId));
        assertEquals(1, recorder.size());

        List<CrossPartyNodeEncounter> snap = recorder.snapshot();
        assertEquals(1, snap.size());
        assertEquals("M1", snap.get(0).mapInstanceId);
        assertEquals("a", snap.get(0).nodeId);

        // Idempotent re-allocate does not double-record.
        assertEquals(a, coordinator.allocateOnConsensus(p0, "a", 1));
        assertEquals(1, recorder.size());
    }

    private static MapDefinition map() {
        return new MapDefinition("M1", "EXORDIUM", 1, "d", "start",
            Arrays.asList(
                new MapNode("start", Arrays.asList("a")),
                new MapNode("a", Arrays.<String>asList())));
    }
}
