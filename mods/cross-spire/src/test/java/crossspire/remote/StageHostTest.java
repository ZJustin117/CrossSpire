package crossspire.remote;

import org.junit.Test;
import static org.junit.Assert.*;

public class StageHostTest {

    @Test
    public void shouldBeStageHostWhenPlayerIdMatches() {
        StageHost host = new StageHost("alice");
        host.setStageHost("alice");
        assertTrue(host.isStageHost());
    }

    @Test
    public void shouldNotBeStageHostWhenPlayerIdDiffers() {
        StageHost host = new StageHost("alice");
        host.setStageHost("bob");
        assertFalse(host.isStageHost());
    }

    @Test
    public void shouldReturnNullStageHostWhenNotSet() {
        StageHost host = new StageHost("alice");
        assertNull(host.getStageHostId());
        assertFalse(host.isStageHost());
    }

    @Test
    public void shouldDetermineStageHostByLexicographicLowest() {
        String[] playerIds = {"zebra", "alpha", "gamma"};
        assertEquals("alpha", StageHost.electHost(playerIds));
    }

    @Test
    public void shouldElectSelfWhenOnlyPlayer() {
        assertEquals("only-me", StageHost.electHost(new String[]{"only-me"}));
    }

    @Test
    public void shouldForceRemoteReferenceForMonsterWhenNotHost() {
        StageHost host = new StageHost("alice");
        host.setStageHost("bob");
        assertFalse("non-host must treat monster as remote", host.canOwnLocally("monster", "Cultist"));
    }

    @Test
    public void shouldAllowLocalReferenceForMonsterWhenHost() {
        StageHost host = new StageHost("alice");
        host.setStageHost("alice");
        assertTrue("host owns monsters locally", host.canOwnLocally("monster", "Cultist"));
    }

    @Test
    public void shouldAllowLocalReferenceForOwnCardRegardlessOfHost() {
        StageHost host = new StageHost("alice");
        host.setStageHost("bob");
        assertTrue("cards are always local to their owner", host.canOwnLocally("card", "Strike_R"));
    }
}
