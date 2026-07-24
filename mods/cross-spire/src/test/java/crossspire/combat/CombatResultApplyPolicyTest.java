package crossspire.combat;

import org.junit.Test;

import static org.junit.Assert.*;

/** T-Test.1–2: combat_result admit / own-skip / hop / owner-fire pure tables. */
public class CombatResultApplyPolicyTest {

    @Test
    public void hostLocalInduceWhenExecutorIsPeer() {
        assertTrue(CombatResultApplyPolicy.shouldLocalInduceAfterBroadcast("host", "peer"));
        assertFalse(CombatResultApplyPolicy.shouldLocalInduceAfterBroadcast("host", "host"));
        assertFalse(CombatResultApplyPolicy.shouldLocalInduceAfterBroadcast("host", ""));
        assertFalse(CombatResultApplyPolicy.shouldLocalInduceAfterBroadcast("host", null));
        assertFalse(CombatResultApplyPolicy.shouldLocalInduceAfterBroadcast("", "peer"));
        assertFalse(CombatResultApplyPolicy.shouldLocalInduceAfterBroadcast(null, "peer"));
    }

    @Test
    public void skipOwnResultWhenExecutorIsLocal() {
        assertTrue(CombatResultApplyPolicy.shouldSkipAsOwnResult("me", "me"));
        assertFalse(CombatResultApplyPolicy.shouldSkipAsOwnResult("me", "peer"));
        assertFalse(CombatResultApplyPolicy.shouldSkipAsOwnResult("me", ""));
        assertFalse(CombatResultApplyPolicy.shouldSkipAsOwnResult("me", null));
        assertFalse(CombatResultApplyPolicy.shouldSkipAsOwnResult(null, "me"));
    }

    @Test
    public void hopDropAtMax() {
        assertFalse(CombatResultApplyPolicy.shouldDropInducedHop(0));
        assertFalse(CombatResultApplyPolicy.shouldDropInducedHop(2));
        assertTrue(CombatResultApplyPolicy.shouldDropInducedHop(3));
        assertTrue(CombatResultApplyPolicy.shouldDropInducedHop(4));
        assertEquals(1, CombatResultApplyPolicy.advanceHopCount(0));
        assertEquals(3, CombatResultApplyPolicy.MAX_INDUCED_HOP);
    }

    @Test
    public void resolveExecutorPrefersExplicitField() {
        assertEquals("e1", CombatResultApplyPolicy.resolveExecutorId("e1", "src"));
        assertEquals("src", CombatResultApplyPolicy.resolveExecutorId("", "src"));
        assertEquals("src", CombatResultApplyPolicy.resolveExecutorId(null, "src"));
        assertEquals("", CombatResultApplyPolicy.resolveExecutorId(null, null));
    }

    @Test
    public void fireLocalOwnerLogicOnlyWhenOwnerMatches() {
        assertTrue(CombatResultApplyPolicy.shouldFireLocalOwnerLogic("me", "me"));
        assertFalse(CombatResultApplyPolicy.shouldFireLocalOwnerLogic("peer", "me"));
        assertFalse(CombatResultApplyPolicy.shouldFireLocalOwnerLogic("", "me"));
        assertFalse(CombatResultApplyPolicy.shouldFireLocalOwnerLogic(null, "me"));
        assertFalse(CombatResultApplyPolicy.shouldFireLocalOwnerLogic("me", ""));
    }
}
