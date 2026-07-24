package crossspire.sync;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Documents the T8.0 contract: only disable(isEnemyTurn=true) is a multiplayer end-turn signal.
 * enable() must never be treated as end-turn.
 */
public class EndTurnTriggerPolicyTest {

    @Test
    public void onlyEnemyTurnDisableIsEndTurnSignal() {
        assertTrue(EndTurnSyncPatches.isMultiplayerEndTurnSignal(true));
        assertFalse(EndTurnSyncPatches.isMultiplayerEndTurnSignal(false));
    }

    @Test
    public void suppressBlocksRegardlessOfFlag() {
        // Policy helper used by patch path; suppress is runtime flag on the patch class.
        assertTrue(EndTurnSyncPatches.shouldBroadcastEndTurn(true, false));
        assertFalse(EndTurnSyncPatches.shouldBroadcastEndTurn(true, true));
        assertFalse(EndTurnSyncPatches.shouldBroadcastEndTurn(false, false));
        assertFalse(EndTurnSyncPatches.shouldBroadcastEndTurn(false, true));
    }
}
