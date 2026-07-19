package crossspire.combat;

import crossspire.network.Protocol;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CombatPhaseTest {

    @Before
    public void setUp() {
        CombatPhaseCoordinator.reset();
    }

    @After
    public void tearDown() {
        CombatPhaseCoordinator.reset();
    }

    @Test
    public void isValidAcceptsSchemaEnum() {
        assertTrue(CombatPhase.isValid(CombatPhase.PLAYER_TURN));
        assertTrue(CombatPhase.isValid(CombatPhase.RESOLVING_QUEUE));
        assertTrue(CombatPhase.isValid(CombatPhase.QUEUE_EMPTY));
        assertTrue(CombatPhase.isValid(CombatPhase.PRE_MONSTER_TURN));
        assertTrue(CombatPhase.isValid(CombatPhase.MONSTER_TURN));
        assertTrue(CombatPhase.isValid(CombatPhase.POST_MONSTER_TURN));
        assertFalse(CombatPhase.isValid("nope"));
        assertFalse(CombatPhase.isValid(null));
    }

    @Test
    public void allowsEndTurnOnlyInQueueEmptyOrPlayerTurn() {
        assertTrue(CombatPhase.allowsEndTurn(CombatPhase.QUEUE_EMPTY));
        assertTrue(CombatPhase.allowsEndTurn(CombatPhase.PLAYER_TURN));
        assertFalse(CombatPhase.allowsEndTurn(CombatPhase.RESOLVING_QUEUE));
        assertFalse(CombatPhase.allowsEndTurn(CombatPhase.PRE_MONSTER_TURN));
        assertFalse(CombatPhase.allowsEndTurn(CombatPhase.MONSTER_TURN));
    }

    @Test
    public void allowsQueueSubmitDelegatesToOrchestrator() {
        assertTrue(CombatPhase.allowsQueueSubmit(CombatPhase.PLAYER_TURN));
        assertFalse(CombatPhase.allowsQueueSubmit(CombatPhase.MONSTER_TURN));
    }

    @Test
    public void applyLocalUpdatesCurrentPhase() {
        CombatPhaseCoordinator.applyLocal(CombatPhase.RESOLVING_QUEUE, "tx1");
        assertEquals(CombatPhase.RESOLVING_QUEUE, CombatPhaseCoordinator.getCurrentPhase());
        assertEquals("tx1", CombatPhaseCoordinator.getLastTransactionId());

        CombatPhaseCoordinator.applyLocal(CombatPhase.QUEUE_EMPTY, "tx2");
        assertEquals(CombatPhase.QUEUE_EMPTY, CombatPhaseCoordinator.getCurrentPhase());
        assertEquals("tx2", CombatPhaseCoordinator.getLastTransactionId());
    }

    @Test
    public void applyLocalIgnoresInvalid() {
        CombatPhaseCoordinator.applyLocal(CombatPhase.PLAYER_TURN, "a");
        CombatPhaseCoordinator.applyLocal("invalid", "b");
        assertEquals(CombatPhase.PLAYER_TURN, CombatPhaseCoordinator.getCurrentPhase());
        assertEquals("a", CombatPhaseCoordinator.getLastTransactionId());
    }

    @Test
    public void buildMessageJsonRoundTrip() {
        String json = CombatPhaseCoordinator.buildMessageJson(
            "host-1", 3, CombatPhase.PRE_MONSTER_TURN, "abc");
        Protocol.CombatPhaseMessage parsed = Protocol.GSON.fromJson(json, Protocol.CombatPhaseMessage.class);
        assertEquals("combat_phase", parsed.type);
        assertEquals("host-1", parsed.source);
        assertEquals(3, parsed.seq);
        assertEquals(CombatPhase.PRE_MONSTER_TURN, parsed.phase);
        assertEquals("abc", parsed.transactionId);
        assertTrue(json.contains("transaction_id"));
        assertTrue(json.contains("pre_monster_turn"));
    }

    @Test
    public void packetOperationConstantMatchesType() {
        assertEquals("combat_phase", CombatPhaseCoordinator.OPERATION);
    }
}
