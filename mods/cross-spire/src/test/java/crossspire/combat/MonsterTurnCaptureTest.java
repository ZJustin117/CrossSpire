package crossspire.combat;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * T6.2: HP/block delta capture pure helpers (no STS engine).
 */
public class MonsterTurnCaptureTest {

    @Test
    public void snapshotAndDeltaDetectDamageAndBlockLoss() {
        MonsterTurnCapture.Snapshot before = new MonsterTurnCapture.Snapshot(80, 10, "player");
        MonsterTurnCapture.Snapshot after = new MonsterTurnCapture.Snapshot(72, 5, "player");
        MonsterTurnCapture.Delta d = MonsterTurnCapture.diff(before, after);
        assertEquals(8, d.damageToPlayer);
        assertEquals(5, d.blockLost);
        assertTrue(d.hasEffects());
    }

    @Test
    public void noDeltaWhenUnchanged() {
        MonsterTurnCapture.Snapshot s = new MonsterTurnCapture.Snapshot(50, 0, "p");
        MonsterTurnCapture.Delta d = MonsterTurnCapture.diff(s, s);
        assertEquals(0, d.damageToPlayer);
        assertEquals(0, d.blockLost);
        assertFalse(d.hasEffects());
    }

    @Test
    public void healDoesNotCountAsDamage() {
        MonsterTurnCapture.Snapshot before = new MonsterTurnCapture.Snapshot(40, 0, "p");
        MonsterTurnCapture.Snapshot after = new MonsterTurnCapture.Snapshot(45, 0, "p");
        MonsterTurnCapture.Delta d = MonsterTurnCapture.diff(before, after);
        assertEquals(0, d.damageToPlayer);
        assertFalse(d.hasEffects());
    }

    @Test
    public void nullSafeDiff() {
        MonsterTurnCapture.Delta d = MonsterTurnCapture.diff(null, new MonsterTurnCapture.Snapshot(1, 0, "p"));
        assertFalse(d.hasEffects());
    }
}
