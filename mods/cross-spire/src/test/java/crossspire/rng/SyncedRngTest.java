package crossspire.rng;

import org.junit.Test;
import static org.junit.Assert.*;

public class SyncedRngTest {

    @Test
    public void sameSeedProducesSameSequence() {
        SyncedRng rng1 = new SyncedRng(220644L);
        SyncedRng rng2 = new SyncedRng(220644L);

        for (int i = 0; i < 100; i++) {
            assertEquals("iteration " + i, rng1.nextInt(100), rng2.nextInt(100));
        }
    }

    @Test
    public void differentSeedsProduceDifferentSequences() {
        SyncedRng rng1 = new SyncedRng(42L);
        SyncedRng rng2 = new SyncedRng(99L);

        boolean same = true;
        for (int i = 0; i < 10; i++) {
            if (rng1.nextInt(100) != rng2.nextInt(100)) {
                same = false;
                break;
            }
        }
        assertFalse("different seeds should produce different numbers", same);
    }

    @Test
    public void seedMatchesInput() {
        SyncedRng rng = new SyncedRng(12345L);
        assertEquals(12345L, rng.getSeed());
    }

    @Test
    public void nextIntWithBoundReturnsInRange() {
        SyncedRng rng = new SyncedRng(42L);
        for (int i = 0; i < 1000; i++) {
            int val = rng.nextInt(20);
            assertTrue("value " + val + " should be in range [0, 20)", val >= 0 && val < 20);
        }
    }
}
