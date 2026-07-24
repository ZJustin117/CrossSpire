package crossspire.sync;

import crossspire.combat.CombatResultApplyPolicy;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * P10.1 / T-Test.1: RoomHost/leader must apply peers' combat_result locally because sendToParty skips self.
 * Binds production {@link CombatResultApplyPolicy} (no mirrored ifs).
 */
public class CombatResultHostApplyPolicyTest {

    @Test
    public void hostMustInduceWhenExecutorIsPeer() {
        String local = "host-id";
        String executor = "peer-id";
        assertTrue(CombatResultApplyPolicy.shouldLocalInduceAfterBroadcast(local, executor));
        assertFalse(CombatResultApplyPolicy.shouldLocalInduceAfterBroadcast(local, local));
        assertFalse(CombatResultApplyPolicy.shouldLocalInduceAfterBroadcast(local, ""));
    }
}
