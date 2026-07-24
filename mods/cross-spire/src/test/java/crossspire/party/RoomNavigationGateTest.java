package crossspire.party;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RoomNavigationGateTest {

    @Test
    public void openLocksUntilUnlock() {
        RoomNavigationGate gate = new RoomNavigationGate();
        gate.onRoomOpened("P0", "node:1");
        assertTrue(gate.blocksRoomPin());
        assertFalse(gate.isExitUnlocked());
        assertTrue(gate.unlock("P0", "node:1", "reward_complete"));
        assertFalse(gate.blocksRoomPin());
        assertTrue(gate.isExitUnlocked());
        assertEquals("reward_complete", gate.getUnlockReason());
    }

    @Test
    public void unlockWrongInstanceRejected() {
        RoomNavigationGate gate = new RoomNavigationGate();
        gate.onRoomOpened("P0", "node:1");
        assertFalse(gate.unlock("P0", "node:other", "x"));
        assertTrue(gate.blocksRoomPin());
    }

    @Test
    public void emptyRoomDoesNotBlockPin() {
        RoomNavigationGate gate = new RoomNavigationGate();
        gate.clear();
        assertFalse(gate.blocksRoomPin());
        gate.onRoomOpened("P0", "");
        assertFalse(gate.blocksRoomPin());
    }

    @Test
    public void reOpenLocksAgain() {
        RoomNavigationGate gate = new RoomNavigationGate();
        gate.onRoomOpened("P0", "node:1");
        gate.unlock("P0", "node:1", "leave");
        gate.onRoomOpened("P0", "node:2");
        assertTrue(gate.blocksRoomPin());
        assertEquals("node:2", gate.getRoomInstanceId());
    }
}
