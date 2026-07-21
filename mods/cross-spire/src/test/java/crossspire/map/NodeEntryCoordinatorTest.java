package crossspire.map;

import crossspire.party.PartyManager;
import crossspire.party.PartyState;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class NodeEntryCoordinatorTest {

    @Test
    public void allocateAfterConsensusIsIdempotentAndPartyScoped() {
        MapDefinition map = map();
        MapRegistry maps = new MapRegistry();
        maps.register("alice", map);
        NodeInstanceRegistry nodes = new NodeInstanceRegistry();
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob"), "");
        parties.bindMap("P0", "M1", "start", "EXORDIUM");
        parties.setNodeInstanceHost("P0", "alice");
        PartyState party = parties.getParty("P0");

        NodeEntryCoordinator coordinator = new NodeEntryCoordinator(maps, nodes);
        NodeInstance first = coordinator.allocateOnConsensus(party, "a", 1);
        assertNotNull(first);
        assertEquals("a", first.nodeId);
        assertEquals("alice", first.nodeInstanceHostId);
        assertSame(first, coordinator.allocateOnConsensus(party, "a", 1));

        parties.leave("bob");
        PartyState p1 = parties.getParty(parties.getPartyIdForPlayer("bob"));
        parties.bindMap(p1.partyId, "M1", "start", "EXORDIUM");
        parties.setNodeInstanceHost(p1.partyId, "bob");
        p1 = parties.getParty(p1.partyId);
        NodeInstance other = coordinator.allocateOnConsensus(p1, "a", 1);
        assertNotNull(other);
        assertEquals(false, first.nodeInstanceId.equals(other.nodeInstanceId));
    }

    @Test
    public void rejectsUnreachableOrUnboundAllocation() {
        MapDefinition map = map();
        MapRegistry maps = new MapRegistry();
        maps.register("alice", map);
        NodeEntryCoordinator coordinator = new NodeEntryCoordinator(maps, new NodeInstanceRegistry());
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice"), "");
        PartyState unbound = parties.getParty("P0");
        assertNull(coordinator.allocateOnConsensus(unbound, "a", 1));

        parties.bindMap("P0", "M1", "start", "EXORDIUM");
        parties.setNodeInstanceHost("P0", "alice");
        PartyState bound = parties.getParty("P0");
        assertNull(coordinator.allocateOnConsensus(bound, "missing", 1));
    }

    private static MapDefinition map() {
        return new MapDefinition("M1", "EXORDIUM", 1, "d", "start",
            Arrays.asList(
                new MapNode("start", Arrays.asList("a")),
                new MapNode("a", Arrays.<String>asList())));
    }
}
