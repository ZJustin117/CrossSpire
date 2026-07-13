package crossspire.rng;

import org.junit.Test;
import static org.junit.Assert.*;

public class RngManagerTest {

    @Test
    public void shouldReturnSameValueForSameNamedStream() {
        RngManager mgr = new RngManager(42L);
        SyncedRng ai1 = mgr.get("aiRng");
        SyncedRng ai2 = mgr.get("aiRng");
        assertSame("same name returns same instance", ai1, ai2);
    }

    @Test
    public void differentStreamsAreIndependent() {
        RngManager mgr = new RngManager(42L);
        SyncedRng ai = mgr.get("aiRng");
        SyncedRng shuffle = mgr.get("shuffleRng");
        assertNotSame("different names, different instances", ai, shuffle);
        boolean allSame = true;
        for (int i = 0; i < 20; i++) {
            if (ai.nextInt(100) != shuffle.nextInt(100)) {
                allSame = false;
                break;
            }
        }
        assertFalse("different streams produce different sequences", allSame);
    }

    @Test
    public void shouldReseedAndRecreate() {
        RngManager mgr = new RngManager(42L);
        SyncedRng before = mgr.get("test");
        int val1 = before.nextInt(100);
        mgr.reseed(99L);
        SyncedRng after = mgr.get("test");
        assertNotSame("after reseed, different instance", before, after);
        assertNotEquals("after reseed, different values", val1, after.nextInt(100));
    }

    @Test
    public void seedingProducesDeterministicStreams() {
        RngManager mgr1 = new RngManager(888L);
        RngManager mgr2 = new RngManager(888L);

        for (int i = 0; i < 50; i++) {
            assertEquals("aiRng step " + i,
                mgr1.get("aiRng").nextInt(500),
                mgr2.get("aiRng").nextInt(500));
        }
    }
}
