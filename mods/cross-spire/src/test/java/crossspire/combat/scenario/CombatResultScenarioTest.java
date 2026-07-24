package crossspire.combat.scenario;

import crossspire.combat.ApplyPowerEffects;
import crossspire.combat.CentralQueueManager;
import crossspire.combat.CombatPhase;
import crossspire.combat.CombatResultApplyPolicy;
import crossspire.combat.MonsterTurnResultGate;
import crossspire.network.Protocol;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Multi-step pure scenarios (no STS): peer induce plan vs personal isolation.
 * T-Test.4 starter set (S1–S8).
 */
public class CombatResultScenarioTest {

    @Test
    public void s1_ownCombatResultSkippedOnExecutorMachine() {
        String local = "player-a";
        String executor = "player-a";
        assertTrue(CombatResultApplyPolicy.shouldSkipAsOwnResult(local, executor));
        assertFalse(CombatResultApplyPolicy.shouldLocalInduceAfterBroadcast(local, executor));
    }

    @Test
    public void s2_peerCombatResultInducedOnNonExecutor() {
        String local = "player-a";
        String executor = "player-b";
        assertFalse(CombatResultApplyPolicy.shouldSkipAsOwnResult(local, executor));
        assertTrue(CombatResultApplyPolicy.shouldLocalInduceAfterBroadcast(local, executor));
    }

    @Test
    public void s3_personalSelfOnlyOnExecutorMachine() {
        String executor = "player-b";
        String peer = "player-a";
        assertTrue(ApplyPowerEffects.isLocalPersonalTarget("self", executor, executor));
        assertFalse(ApplyPowerEffects.isLocalPersonalTarget("self", executor, peer));
        assertTrue(ApplyPowerEffects.isLocalPersonalTarget(executor, executor, executor));
        assertFalse(ApplyPowerEffects.isLocalPersonalTarget("Cultist", executor, peer));
    }

    @Test
    public void s4_nonOwnerDoesNotFireLocalLogic() {
        String local = "player-a";
        String logicOwner = "player-b";
        assertFalse(CombatResultApplyPolicy.shouldFireLocalOwnerLogic(logicOwner, local));
        assertTrue(CombatResultApplyPolicy.shouldFireLocalOwnerLogic(local, local));
    }

    @Test
    public void s5_inducedHopAtCapIsDropped() {
        int hop = CombatResultApplyPolicy.MAX_INDUCED_HOP;
        assertTrue(CombatResultApplyPolicy.shouldDropInducedHop(hop));
        assertFalse(CombatResultApplyPolicy.shouldDropInducedHop(hop - 1));
        assertEquals(hop, CombatResultApplyPolicy.advanceHopCount(hop - 1));
    }

    @Test
    public void s6_illegalPhaseRejectsEndTurnAndQueueSubmit() {
        assertFalse(CombatPhase.allowsEndTurn(CombatPhase.RESOLVING_QUEUE));
        assertFalse(CombatPhase.allowsEndTurn(CombatPhase.MONSTER_TURN));
        assertFalse(CombatPhase.allowsQueueSubmit(CombatPhase.MONSTER_TURN));
        assertTrue(CombatPhase.allowsQueueSubmit(CombatPhase.PLAYER_TURN));
        assertTrue(CombatPhase.allowsEndTurn(CombatPhase.QUEUE_EMPTY));
    }

    @Test
    public void s7_monsterTurnAdmitStageHostAndTransaction() {
        MonsterTurnResultGate gate = new MonsterTurnResultGate();
        assertTrue(gate.admit(CombatPhase.MONSTER_TURN, "tx1", "monster_turn",
            "host", "host", 1, "tx1"));
        assertFalse(gate.admit(CombatPhase.MONSTER_TURN, "tx1", "monster_turn",
            "peer", "host", 2, "tx1"));
        assertFalse(gate.admit(CombatPhase.MONSTER_TURN, "tx1", "monster_turn",
            "host", "host", 1, "tx1"));
    }

    @Test
    public void s8_queueSourceSeqDedup() {
        CentralQueueManager mgr = new CentralQueueManager();
        Protocol.QueueSubmitMessage pkt = new Protocol.QueueSubmitMessage();
        pkt.cardId = "Strike_R";
        pkt.timestamp = 100;
        pkt.senderId = "alice";
        pkt.source = "alice";
        pkt.seq = 1;
        pkt.ownerId = "alice";
        mgr.onQueueSubmit(pkt);
        mgr.onQueueSubmit(pkt);
        assertEquals(1, mgr.size());
    }
}
