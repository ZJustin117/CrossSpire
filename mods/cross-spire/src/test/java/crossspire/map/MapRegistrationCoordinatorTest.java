package crossspire.map;

import crossspire.party.PartyManager;
import crossspire.party.PartyState;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MapRegistrationCoordinatorTest {

    @Test
    public void onlyElectedMapHostCanRegisterForItsParty() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob"), "");
        PartyState party = parties.getParty("P0");
        PartyHostElectionTracker elections = new PartyHostElectionTracker();
        elections.castMapHostVote(party, "alice", "bob");
        elections.castMapHostVote(party, "bob", "bob");
        MapRegistrationCoordinator coordinator = new MapRegistrationCoordinator(new MapRegistry(), elections);
        MapDefinition map = map("M1");

        assertNull(coordinator.register(party, "alice", "bob", map));
        assertNull(coordinator.register(party, "bob", "alice", map));
        assertNotNull(coordinator.register(party, "bob", "bob", map));
    }

    @Test
    public void rejectsRegistrationBeforeElectionConsensus() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob"), "");
        PartyState party = parties.getParty("P0");
        PartyHostElectionTracker elections = new PartyHostElectionTracker();
        elections.castMapHostVote(party, "alice", "alice");
        MapRegistrationCoordinator coordinator = new MapRegistrationCoordinator(new MapRegistry(), elections);

        assertNull(coordinator.register(party, "alice", "alice", map("M1")));
    }

    private static MapDefinition map(String id) {
        return new MapDefinition(id, "EXORDIUM", 1, "digest", "start",
            Arrays.asList(new MapNode("start", Arrays.<String>asList())));
    }
}
