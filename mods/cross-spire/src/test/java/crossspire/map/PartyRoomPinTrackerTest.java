package crossspire.map;

import crossspire.party.PartyManager;
import crossspire.party.PartyState;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PartyRoomPinTrackerTest {

    @Test
    public void consensusRequiresAllPartyMembersOnSameNode() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob", "charlie"), "start");
        PartyState party = parties.getParty("P0");
        PartyRoomPinTracker tracker = new PartyRoomPinTracker();

        assertTrue(tracker.pin(party, "alice", "node1"));
        assertTrue(tracker.pin(party, "bob", "node1"));
        assertNull(tracker.consensusNodeId(party));
        assertTrue(tracker.pin(party, "charlie", "node1"));
        assertEquals("node1", tracker.consensusNodeId(party));
    }

    @Test
    public void pinsAreIsolatedByPartyAndRejectOutsiders() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob", "charlie"), "start");
        PartyState p1 = parties.leave("charlie");
        PartyState p0 = parties.getParty("P0");
        PartyRoomPinTracker tracker = new PartyRoomPinTracker();

        assertFalse(tracker.pin(p0, "charlie", "node1"));
        assertTrue(tracker.pin(p0, "alice", "node1"));
        assertTrue(tracker.pin(p0, "bob", "node1"));
        assertEquals("node1", tracker.consensusNodeId(p0));
        assertTrue(tracker.pin(p1, "charlie", "other"));
        assertEquals("other", tracker.consensusNodeId(p1));
    }

    @Test
    public void resolveOutgoingUsesImmutableTopology() {
        MapDefinition map = new MapDefinition("M1", "EXORDIUM", 1, "d", "start",
            Arrays.asList(
                new MapNode("start", Arrays.asList("a", "b")),
                new MapNode("a", Arrays.<String>asList()),
                new MapNode("b", Arrays.<String>asList())));
        assertEquals("a", MapNavigation.resolveOutgoing(map, "start", 0));
        assertEquals("b", MapNavigation.resolveOutgoing(map, "start", 1));
        assertNull(MapNavigation.resolveOutgoing(map, "start", 2));
        assertNull(MapNavigation.resolveOutgoing(map, "missing", 0));
    }

    @Test
    public void concurrentPartiesOnSharedMapDoNotSharePinConsensus() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob", "carol", "dave"), "0:-1");
        PartyState soloA = parties.leave("carol");
        // leave bob after carol so P0 keeps alice+dave, soloA is carol's party; create second solo via leave dave
        PartyState soloB = parties.leave("dave");
        parties.bindMap("P0", "M1", "0:-1", "EXORDIUM");
        parties.bindMap(soloA.partyId, "M1", "0:-1", "EXORDIUM");
        parties.bindMap(soloB.partyId, "M1", "0:-1", "EXORDIUM");
        PartyState p0 = parties.getParty("P0");
        soloA = parties.getParty(soloA.partyId);
        soloB = parties.getParty(soloB.partyId);
        PartyRoomPinTracker tracker = new PartyRoomPinTracker();

        assertTrue(tracker.pin(p0, "alice", "0:0"));
        assertTrue(tracker.pin(soloA, "carol", "1:0"));
        assertNull(tracker.consensusNodeId(p0));
        assertEquals("1:0", tracker.consensusNodeId(soloA));
        assertTrue(tracker.pin(p0, "bob", "0:0"));
        assertEquals("0:0", tracker.consensusNodeId(p0));
        assertNull(tracker.consensusNodeId(soloB));
        assertTrue(tracker.pin(soloB, "dave", "2:0"));
        assertEquals("2:0", tracker.consensusNodeId(soloB));
        assertEquals("0:0", tracker.consensusNodeId(p0));
        assertEquals("1:0", tracker.consensusNodeId(soloA));
    }

    @Test
    public void clearRemovesPinsSoNextVisitNeedsFreshUnanimousVotes() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob"), "start");
        PartyState party = parties.getParty("P0");
        PartyRoomPinTracker tracker = new PartyRoomPinTracker();

        assertTrue(tracker.pin(party, "alice", "node1"));
        assertTrue(tracker.pin(party, "bob", "node1"));
        assertEquals("node1", tracker.consensusNodeId(party));
        tracker.clear("P0");
        assertNull(tracker.consensusNodeId(party));
        assertTrue(tracker.pin(party, "alice", "node2"));
        assertNull(tracker.consensusNodeId(party));
        assertTrue(tracker.pin(party, "bob", "node2"));
        assertEquals("node2", tracker.consensusNodeId(party));
    }
}
