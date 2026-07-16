package crossspire.network;

import org.junit.Test;
import static org.junit.Assert.*;

public class HeartbeatManagerTest {

    @Test
    public void initialLastSeenShouldBeNullForUnknownPeer() {
        HeartbeatManager.resetForTest();
        assertEquals(0L, HeartbeatManager.getLastSeen("unknown-peer"));
    }

    @Test
    public void handlePongShouldUpdateTimestamp() {
        HeartbeatManager.resetForTest();
        HeartbeatManager.handlePong("alice");
        long ts = HeartbeatManager.getLastSeen("alice");
        assertTrue("timestamp should be set after pong", ts > 0);
    }

    @Test
    public void shouldDetectTimeoutForStalePeer() {
        HeartbeatManager.resetForTest();
        // Set timestamp 31s ago
        HeartbeatManager.testSetLastSeen("bob", System.currentTimeMillis() - 31000);
        assertTrue(HeartbeatManager.isTimedOut("bob", 30000));
    }

    @Test
    public void shouldNotDetectTimeoutForRecentPeer() {
        HeartbeatManager.resetForTest();
        HeartbeatManager.handlePong("bob");
        assertFalse(HeartbeatManager.isTimedOut("bob", 30000));
    }
}
