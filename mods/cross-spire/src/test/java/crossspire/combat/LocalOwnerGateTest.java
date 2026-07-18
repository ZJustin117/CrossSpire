package crossspire.combat;

import crossspire.CrossSpireMod;
import crossspire.reference.Reference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class LocalOwnerGateTest {

    private String savedPlayerId;

    private static final class StubRef extends Reference<Object> {
        StubRef(String ownerId) {
            super("stub@" + ownerId, ownerId, Type.LOCAL, "");
        }

        @Override
        public void dereference(Object... args) {}
    }

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
    public void isLocalOwnerMatchesSelf() {
        assertTrue(LocalOwnerGate.isLocalOwner("player-self"));
        assertFalse(LocalOwnerGate.isLocalOwner("player-other"));
        assertFalse(LocalOwnerGate.isLocalOwner((String) null));
        assertFalse(LocalOwnerGate.isLocalOwner(""));
        assertFalse(LocalOwnerGate.isLocalOwner((Reference<?>) null));
    }

    @Test
    public void isLocalOwnerUsesReferenceOwner() {
        Reference<?> local = new StubRef("player-self");
        Reference<?> remote = new StubRef("player-other");
        assertTrue(LocalOwnerGate.isLocalOwner(local));
        assertFalse(LocalOwnerGate.isLocalOwner(remote));
    }

    @Test
    public void mayFirePassivePrefersExplicitLogicOwner() {
        Reference<?> remoteOwnedRef = new StubRef("player-other");
        assertTrue(LocalOwnerGate.mayFirePassive("player-self", remoteOwnedRef));
        assertFalse(LocalOwnerGate.mayFirePassive("player-other", remoteOwnedRef));
        assertFalse(LocalOwnerGate.mayFirePassive(null, remoteOwnedRef));
        assertTrue(LocalOwnerGate.mayFirePassive(null, new StubRef("player-self")));
    }
}
