package crossspire.party;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PartyVisibilityTest {

    @Test
    public void returnsOnlyRemoteMembersOfTheLocalParty() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob", "charlie"), "node-4");
        manager.leave("charlie");

        assertEquals(Arrays.asList("bob"), PartyVisibility.visibleRemotePlayerIds(
            manager, "alice", Arrays.asList("alice", "bob", "charlie", "dana")));
    }

    @Test
    public void missingLocalPartyShowsNoRemoteGameplayProjection() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob"), "node-4");

        assertEquals(Arrays.asList(), PartyVisibility.visibleRemotePlayerIds(
            manager, "charlie", Arrays.asList("alice", "bob")));
    }
}
