package crossspire.combat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T7.0c: monster-turn completion must be current, authoritative, and unique. */
public class MonsterTurnResultGateTest {

    @Test
    public void acceptsOneZeroEffectResultFromCurrentStageHost() {
        MonsterTurnResultGate gate = new MonsterTurnResultGate();
        assertTrue(gate.admit(CombatPhase.MONSTER_TURN, "turn-1", "monster_turn",
            "stage-host", "stage-host", 7, "turn-1"));
        assertFalse(gate.admit(CombatPhase.MONSTER_TURN, "turn-1", "monster_turn",
            "stage-host", "stage-host", 7, "turn-1"));
    }

    @Test
    public void rejectsWrongSourceAndWrongPhase() {
        MonsterTurnResultGate gate = new MonsterTurnResultGate();
        assertFalse(gate.admit(CombatPhase.MONSTER_TURN, "turn-1", "monster_turn",
            "other-player", "stage-host", 8, "turn-1"));
        assertFalse(gate.admit(CombatPhase.PLAYER_TURN, "turn-1", "monster_turn",
            "stage-host", "stage-host", 8, "turn-1"));
    }

    @Test
    public void rejectsDelayedPriorTurnAndAcceptsNextTurn() {
        MonsterTurnResultGate gate = new MonsterTurnResultGate();
        assertFalse(gate.admit(CombatPhase.MONSTER_TURN, "turn-2", "monster_turn",
            "stage-host", "stage-host", 9, "turn-1"));
        assertTrue(gate.admit(CombatPhase.MONSTER_TURN, "turn-2", "monster_turn",
            "stage-host", "stage-host", 10, "turn-2"));
    }
}
