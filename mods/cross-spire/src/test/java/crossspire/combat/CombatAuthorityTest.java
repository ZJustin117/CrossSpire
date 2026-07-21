package crossspire.combat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T7.0 authority and transaction validation without the STS engine. */
public class CombatAuthorityTest {

    @Before
    public void setUp() {
        CombatPhaseCoordinator.reset();
    }

    @After
    public void tearDown() {
        CombatPhaseCoordinator.reset();
    }

    @Test
    public void multiplayerNonStageHostSuppressesMonsterAi() {
        assertTrue(CombatTurnOrchestrator.shouldSuppressMonsterAi(true, false));
        assertFalse(CombatTurnOrchestrator.shouldSuppressMonsterAi(true, true));
        assertFalse(CombatTurnOrchestrator.shouldSuppressMonsterAi(false, false));
    }

    @Test
    public void onlyConnectedStageHostMayRunMonsterAiDuringMonsterTurn() {
        assertTrue(CombatTurnOrchestrator.shouldAllowMonsterAi(
            true, true, CombatPhase.MONSTER_TURN));
        assertFalse(CombatTurnOrchestrator.shouldAllowMonsterAi(
            true, true, CombatPhase.PLAYER_TURN));
        assertFalse(CombatTurnOrchestrator.shouldAllowMonsterAi(
            true, false, CombatPhase.MONSTER_TURN));
        assertTrue(CombatTurnOrchestrator.shouldAllowMonsterAi(
            false, false, CombatPhase.PLAYER_TURN));
    }

    @Test
    public void remotePhaseRequiresRoomHostSource() {
        assertFalse(CombatPhaseCoordinator.tryApplyRemote(
            "other-player", "room-host", CombatPhase.QUEUE_EMPTY, "tx-1"));
        assertTrue(CombatPhaseCoordinator.tryApplyRemote(
            "room-host", "room-host", CombatPhase.QUEUE_EMPTY, "tx-1"));
    }

    @Test
    public void remotePhaseRejectsDuplicateTransactionAndIllegalTransition() {
        assertTrue(CombatPhaseCoordinator.tryApplyRemote(
            "room-host", "room-host", CombatPhase.QUEUE_EMPTY, "tx-1"));
        assertFalse(CombatPhaseCoordinator.tryApplyRemote(
            "room-host", "room-host", CombatPhase.QUEUE_EMPTY, "tx-1"));
        assertFalse(CombatPhaseCoordinator.tryApplyRemote(
            "room-host", "room-host", CombatPhase.MONSTER_TURN, "tx-2"));
        assertTrue(CombatPhaseCoordinator.tryApplyRemote(
            "room-host", "room-host", CombatPhase.PRE_MONSTER_TURN, "tx-3"));
    }
}
