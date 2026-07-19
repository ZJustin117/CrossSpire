package crossspire.combat;

import crossspire.network.Protocol;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * T5.2: Bash-style apply_power carries logic_owner and registers ComponentAttachment.
 */
public class ApplyPowerOwnershipTest {

    @Before
    public void setUp() {
        ComponentAttachmentRegistry.clear();
    }

    @After
    public void tearDown() {
        ComponentAttachmentRegistry.clear();
    }

    @Test
    public void bashEffectsRegisterAttachmentWithApplierOwner() {
        Protocol.EffectDescription[] effects = ApplyPowerEffects.buildCardEffects(
            8, 0, 2, "Cultist", "player-a");

        for (Protocol.EffectDescription e : effects) {
            if (!"apply_power".equals(e.kind)) continue;
            String host = ComponentAttachmentRegistry.hostEntityIdForTarget(e.target);
            ComponentAttachmentRegistry.registerApplyPower(
                e.powerId, e.logicOwnerId, host, e.amount);
        }

        assertEquals(1, ComponentAttachmentRegistry.size());
        ComponentAttachment att = ComponentAttachmentRegistry.getByHost("monster:Cultist").get(0);
        assertEquals("Vulnerable", att.powerId);
        assertEquals("player-a", att.logicOwnerId);
        assertEquals(2, att.amount);
    }

    @Test
    public void nonOwnerAttachmentDoesNotMatchLocalGate() {
        ComponentAttachmentRegistry.registerApplyPower(
            "Vulnerable", "player-other", "monster:Cultist", 2);
        ComponentAttachment att = ComponentAttachmentRegistry.getByHost("monster:Cultist").get(0);
        assertFalse(LocalOwnerGate.isLocalOwner(att.logicOwnerId));
    }
}
