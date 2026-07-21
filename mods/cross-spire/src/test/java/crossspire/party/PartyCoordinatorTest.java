package crossspire.party;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PartyCoordinatorTest {

    @Test
    public void onlyCurrentPartyLeaderCanCoordinateThatParty() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob", "charlie"), "node-4");
        PartyState p1 = manager.leave("charlie");

        assertTrue(PartyCoordinator.isLeader(manager, "P0", "alice"));
        assertFalse(PartyCoordinator.isLeader(manager, "P0", "bob"));
        assertFalse(PartyCoordinator.isLeader(manager, "P0", "charlie"));
        assertTrue(PartyCoordinator.isLeader(manager, p1.partyId, "charlie"));
    }

    @Test
    public void memberMaySubmitButOnlyLeaderMayCoordinate() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob"), "node-4");

        assertTrue(PartyCoordinator.isMember(manager, "P0", "bob"));
        assertFalse(PartyCoordinator.isMember(manager, "P0", "charlie"));
        assertFalse(PartyCoordinator.isLeader(manager, "P0", "bob"));
    }
}
