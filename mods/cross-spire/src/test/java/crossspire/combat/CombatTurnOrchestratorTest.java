package crossspire.combat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * T6.1: room-host combat turn state machine (pure logic; no network).
 */
public class CombatTurnOrchestratorTest {

    @Before
    public void setUp() {
        CombatPhaseCoordinator.reset();
        CombatTurnOrchestrator.reset();
    }

    @After
    public void tearDown() {
        CombatPhaseCoordinator.reset();
        CombatTurnOrchestrator.reset();
    }

    @Test
    public void endTurnConsensusNextIsPreMonster() {
        assertEquals(CombatPhase.PRE_MONSTER_TURN,
            CombatTurnOrchestrator.nextAfterEndTurnConsensus());
    }

    @Test
    public void legalTransitionsForFullRound() {
        assertTrue(CombatTurnOrchestrator.canTransition(
            CombatPhase.QUEUE_EMPTY, CombatPhase.PRE_MONSTER_TURN));
        assertTrue(CombatTurnOrchestrator.canTransition(
            CombatPhase.PLAYER_TURN, CombatPhase.PRE_MONSTER_TURN));
        assertTrue(CombatTurnOrchestrator.canTransition(
            CombatPhase.PRE_MONSTER_TURN, CombatPhase.MONSTER_TURN));
        assertTrue(CombatTurnOrchestrator.canTransition(
            CombatPhase.MONSTER_TURN, CombatPhase.POST_MONSTER_TURN));
        assertTrue(CombatTurnOrchestrator.canTransition(
            CombatPhase.POST_MONSTER_TURN, CombatPhase.PLAYER_TURN));
        assertTrue(CombatTurnOrchestrator.canTransition(
            CombatPhase.PLAYER_TURN, CombatPhase.RESOLVING_QUEUE));
        assertTrue(CombatTurnOrchestrator.canTransition(
            CombatPhase.RESOLVING_QUEUE, CombatPhase.QUEUE_EMPTY));
    }

    @Test
    public void illegalTransitionsRejected() {
        assertFalse(CombatTurnOrchestrator.canTransition(
            CombatPhase.RESOLVING_QUEUE, CombatPhase.MONSTER_TURN));
        assertFalse(CombatTurnOrchestrator.canTransition(
            CombatPhase.MONSTER_TURN, CombatPhase.PLAYER_TURN));
        assertFalse(CombatTurnOrchestrator.canTransition(
            CombatPhase.QUEUE_EMPTY, CombatPhase.MONSTER_TURN));
        assertFalse(CombatTurnOrchestrator.canTransition(null, CombatPhase.PLAYER_TURN));
        assertFalse(CombatTurnOrchestrator.canTransition(CombatPhase.PLAYER_TURN, null));
    }

    @Test
    public void allowsQueueSubmitOnlyInPlayerSidePhases() {
        assertTrue(CombatTurnOrchestrator.allowsQueueSubmit(CombatPhase.PLAYER_TURN));
        assertTrue(CombatTurnOrchestrator.allowsQueueSubmit(CombatPhase.QUEUE_EMPTY));
        assertTrue(CombatTurnOrchestrator.allowsQueueSubmit(CombatPhase.RESOLVING_QUEUE));
        assertFalse(CombatTurnOrchestrator.allowsQueueSubmit(CombatPhase.PRE_MONSTER_TURN));
        assertFalse(CombatTurnOrchestrator.allowsQueueSubmit(CombatPhase.MONSTER_TURN));
        assertFalse(CombatTurnOrchestrator.allowsQueueSubmit(CombatPhase.POST_MONSTER_TURN));
    }

    @Test
    public void applyLocalSequenceAdvancesCoordinator() {
        CombatPhaseCoordinator.applyLocal(CombatPhase.QUEUE_EMPTY, "t0");
        assertTrue(CombatTurnOrchestrator.tryApplyTransition(CombatPhase.PRE_MONSTER_TURN, "t1"));
        assertEquals(CombatPhase.PRE_MONSTER_TURN, CombatPhaseCoordinator.getCurrentPhase());

        assertTrue(CombatTurnOrchestrator.tryApplyTransition(CombatPhase.MONSTER_TURN, "t2"));
        assertEquals(CombatPhase.MONSTER_TURN, CombatPhaseCoordinator.getCurrentPhase());

        assertTrue(CombatTurnOrchestrator.tryApplyTransition(CombatPhase.POST_MONSTER_TURN, "t3"));
        assertEquals(CombatPhase.POST_MONSTER_TURN, CombatPhaseCoordinator.getCurrentPhase());

        assertTrue(CombatTurnOrchestrator.tryApplyTransition(CombatPhase.PLAYER_TURN, "t4"));
        assertEquals(CombatPhase.PLAYER_TURN, CombatPhaseCoordinator.getCurrentPhase());
    }

    @Test
    public void tryApplyTransitionRejectsIllegal() {
        CombatPhaseCoordinator.applyLocal(CombatPhase.RESOLVING_QUEUE, "r");
        assertFalse(CombatTurnOrchestrator.tryApplyTransition(CombatPhase.MONSTER_TURN, "x"));
        assertEquals(CombatPhase.RESOLVING_QUEUE, CombatPhaseCoordinator.getCurrentPhase());
    }

    @Test
    public void nextAfterMonsterTurnIsPostThenPlayer() {
        assertEquals(CombatPhase.POST_MONSTER_TURN,
            CombatTurnOrchestrator.nextAfterMonsterTurnComplete());
        assertEquals(CombatPhase.PLAYER_TURN,
            CombatTurnOrchestrator.nextAfterPostMonster());
    }

    @Test
    public void stageHostShouldRunMonsterAiOnlyInMonsterTurn() {
        CombatPhaseCoordinator.applyLocal(CombatPhase.PLAYER_TURN, "a");
        assertFalse(CombatTurnOrchestrator.shouldStageHostRunMonsterAi());

        CombatPhaseCoordinator.applyLocal(CombatPhase.MONSTER_TURN, "b");
        assertTrue(CombatTurnOrchestrator.shouldStageHostRunMonsterAi());
    }
}
