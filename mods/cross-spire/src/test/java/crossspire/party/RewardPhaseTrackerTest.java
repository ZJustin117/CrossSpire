package crossspire.party;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RewardPhaseTrackerTest {

    @Test
    public void blocksPinUntilAllDone() {
        RewardPhaseTracker tracker = new RewardPhaseTracker();
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob"), "n0");
        PartyState party = manager.getParty("P0");

        assertTrue(tracker.enter("P0", "node:1"));
        assertTrue(tracker.blocksRoomPin());
        assertFalse(tracker.markDone("alice", party));
        assertEquals(1, tracker.doneCount());
        assertTrue(tracker.markDone("bob", party));
        tracker.clear();
        assertFalse(tracker.blocksRoomPin());
        assertTrue(tracker.isCompletedNode("node:1"));
    }

    @Test
    public void rejectsNonMember() {
        RewardPhaseTracker tracker = new RewardPhaseTracker();
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob"), "n0");
        tracker.enter("P0", "node:1");
        assertFalse(tracker.markDone("charlie", manager.getParty("P0")));
    }

    @Test
    public void lateEnterAfterCompleteDoesNotReblockPin() {
        RewardPhaseTracker tracker = new RewardPhaseTracker();
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob"), "n0");
        PartyState party = manager.getParty("P0");

        tracker.enter("P0", "node:1");
        tracker.markDone("alice", party);
        tracker.markDone("bob", party);
        tracker.clear();
        assertFalse(tracker.blocksRoomPin());

        assertFalse(tracker.enter("P0", "node:1"));
        assertFalse(tracker.blocksRoomPin());
        assertFalse(tracker.markDone("alice", party));
    }

    @Test
    public void enterSameActiveNodeIsIdempotent() {
        RewardPhaseTracker tracker = new RewardPhaseTracker();
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob"), "n0");
        PartyState party = manager.getParty("P0");

        assertTrue(tracker.enter("P0", "node:1"));
        tracker.markDone("alice", party);
        assertTrue(tracker.enter("P0", "node:1"));
        assertEquals(1, tracker.doneCount());
        assertTrue(tracker.blocksRoomPin());
    }
}
