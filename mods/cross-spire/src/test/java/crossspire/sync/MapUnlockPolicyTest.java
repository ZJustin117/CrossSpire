package crossspire.sync;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapUnlockPolicyTest {

    @Test
    public void dedupesRapidUnlockBroadcastsForSameRoom() {
        // reset static state via first call
        MapUnlockPatches.shouldBroadcastUnlock("node:a", 1000L);
        assertFalse(MapUnlockPatches.shouldBroadcastUnlock("node:a", 1100L));
        assertTrue(MapUnlockPatches.shouldBroadcastUnlock("node:a", 3000L));
        assertTrue(MapUnlockPatches.shouldBroadcastUnlock("node:b", 3100L));
    }
}
