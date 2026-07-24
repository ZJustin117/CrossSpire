package crossspire.map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MapHostRegistrationCoordinatorTest {

    @Test
    public void remembersPendingElectionUntilMapGenerationCompletes() {
        MapHostRegistrationCoordinator.rememberElection("P0", "player-a");

        assertEquals("player-a", MapHostRegistrationCoordinator.electedHost("P0"));
        MapHostRegistrationCoordinator.clearElection("P0");
        assertNull(MapHostRegistrationCoordinator.electedHost("P0"));
    }
}
