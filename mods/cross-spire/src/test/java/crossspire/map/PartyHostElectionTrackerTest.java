package crossspire.map;

import crossspire.party.PartyManager;
import crossspire.party.PartyState;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class PartyHostElectionTrackerTest {

    @Test
    public void mapHostElectionRequiresPartyWideConsensus() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob", "charlie"), "node-4");
        PartyState party = manager.getParty("P0");
        PartyHostElectionTracker tracker = new PartyHostElectionTracker();

        assertTrue(tracker.castMapHostVote(party, "alice", "bob"));
        assertTrue(tracker.castMapHostVote(party, "bob", "bob"));
        assertNull(tracker.mapHostConsensus(party));
        assertTrue(tracker.castMapHostVote(party, "charlie", "bob"));
        assertEquals("bob", tracker.mapHostConsensus(party));
    }

    @Test
    public void electionsAreIndependentByRoleAndParty() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob", "charlie"), "node-4");
        PartyState p1 = manager.leave("charlie");
        PartyState p0 = manager.getParty("P0");
        PartyHostElectionTracker tracker = new PartyHostElectionTracker();

        assertTrue(tracker.castMapHostVote(p0, "alice", "alice"));
        assertTrue(tracker.castMapHostVote(p0, "bob", "alice"));
        assertEquals("alice", tracker.mapHostConsensus(p0));
        assertTrue(tracker.castNodeInstanceHostVote(p0, "alice", "bob"));
        assertTrue(tracker.castNodeInstanceHostVote(p0, "bob", "bob"));
        assertEquals("bob", tracker.nodeInstanceHostConsensus(p0));

        assertTrue(tracker.castMapHostVote(p1, "charlie", "charlie"));
        assertEquals("charlie", tracker.mapHostConsensus(p1));
    }

    @Test
    public void rejectsOutsidersAndAllowsVoteReplacement() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob", "charlie"), "node-4");
        manager.leave("charlie");
        PartyState p0 = manager.getParty("P0");
        PartyHostElectionTracker tracker = new PartyHostElectionTracker();

        assertFalse(tracker.castMapHostVote(p0, "charlie", "alice"));
        assertFalse(tracker.castMapHostVote(p0, "alice", "charlie"));
        assertTrue(tracker.castMapHostVote(p0, "alice", "alice"));
        assertTrue(tracker.castMapHostVote(p0, "bob", "alice"));
        assertEquals("alice", tracker.mapHostConsensus(p0));
        assertTrue(tracker.castMapHostVote(p0, "bob", "bob"));
        assertNull(tracker.mapHostConsensus(p0));
    }
}
