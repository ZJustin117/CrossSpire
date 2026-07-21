package crossspire.party;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PartyMapBindingTest {

    @Test
    public void bindMapSetsMapActiveAndStartNodeAndClearsNodeInstanceHost() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob"), "");
        manager.setNodeInstanceHost("P0", "alice"); // rejected without map
        assertEquals("", manager.getParty("P0").nodeInstanceHostId);

        PartyState bound = manager.bindMap("P0", "M1", "start", "EXORDIUM");
        assertNotNull(bound);
        assertEquals(PartyState.PHASE_MAP_ACTIVE, bound.phaseStatus);
        assertEquals("M1", bound.mapInstanceId);
        assertEquals("start", bound.mapPosition);
        assertEquals("EXORDIUM", bound.actId);
        assertEquals("", bound.nodeInstanceHostId);
        assertTrue(bound.partyRevision > 0);
    }

    @Test
    public void nodeInstanceHostRequiresMapBindingAndMembership() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob"), "");
        assertNull(manager.setNodeInstanceHost("P0", "alice"));

        manager.bindMap("P0", "M1", "start", "EXORDIUM");
        assertNull(manager.setNodeInstanceHost("P0", "charlie"));
        PartyState withHost = manager.setNodeInstanceHost("P0", "bob");
        assertNotNull(withHost);
        assertEquals("bob", withHost.nodeInstanceHostId);
    }
}
