package crossspire.combat;

import crossspire.party.PartyManager;
import crossspire.party.PartyState;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.*;

public class PartyEndTurnTrackerTest {

    @Test
    public void consensusIsScopedToThePartyAndRejectsOutsiders() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("a", "b", "c"), "m");
        PartyState p0 = manager.getParty("P0");
        PartyState p1 = manager.leave("c");
        PartyEndTurnTracker tracker = new PartyEndTurnTracker();

        assertFalse(tracker.markReady(p0, "a"));
        assertTrue(tracker.markReady(p1, "c"));
        assertTrue(tracker.hasConsensus(p1));
        assertFalse(tracker.hasConsensus(p0));
        assertFalse(tracker.markReady(p0, "c"));
        assertTrue(tracker.markReady(p0, "b"));
    }

    @Test
    public void clearingOnePartyLeavesOtherReadinessUntouched() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("a", "b"), "m");
        PartyState p0 = manager.getParty("P0");
        PartyState p1 = manager.leave("b");
        PartyEndTurnTracker tracker = new PartyEndTurnTracker();

        tracker.markReady(p0, "a");
        tracker.markReady(p1, "b");
        tracker.clear("P0");

        assertEquals(0, tracker.readyCount("P0"));
        assertTrue(tracker.hasConsensus(p1));
    }
}
