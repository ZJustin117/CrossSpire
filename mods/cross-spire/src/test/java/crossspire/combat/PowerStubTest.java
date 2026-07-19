package crossspire.combat;

import crossspire.CrossSpireMod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests PowerLogicGate (used by PowerStub) without loading AbstractPower / STS jar.
 */
public class PowerStubTest {

    private String savedPlayerId;

    @Before
    public void setUp() {
        savedPlayerId = CrossSpireMod.playerId;
        CrossSpireMod.playerId = "player-self";
    }

    @After
    public void tearDown() {
        CrossSpireMod.playerId = savedPlayerId;
    }

    @Test
    public void isLocalLogicOwnerRespectsGate() {
        PowerLogicGate local = new PowerLogicGate("player-self");
        PowerLogicGate remote = new PowerLogicGate("player-other");
        PowerLogicGate unknown = new PowerLogicGate(null);

        assertTrue(local.isLocalLogicOwner());
        assertFalse(remote.isLocalLogicOwner());
        assertFalse(unknown.isLocalLogicOwner());
    }

    @Test
    public void nonOwnerAllowLogicBlocksAndRecords() {
        PowerLogicGate remote = new PowerLogicGate("player-other");
        assertFalse(remote.allowLogic());
        assertTrue(remote.wasBlockedByOwnership());
        assertFalse(remote.allowLogic());
    }

    @Test
    public void localOwnerAllowLogicDoesNotBlock() {
        PowerLogicGate local = new PowerLogicGate("player-self");
        assertTrue(local.allowLogic());
        assertFalse(local.wasBlockedByOwnership());
    }
}
