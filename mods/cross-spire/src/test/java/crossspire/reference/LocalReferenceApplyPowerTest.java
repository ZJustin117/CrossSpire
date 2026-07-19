package crossspire.reference;

import crossspire.combat.ApplyPowerEffects;
import crossspire.network.Protocol;
import org.junit.Test;

import static org.junit.Assert.*;

public class LocalReferenceApplyPowerTest {

    @Test
    public void applyPowerEffectSetsKindAndLogicOwner() {
        Protocol.EffectDescription eff = ApplyPowerEffects.applyPowerEffect(
            "Vulnerable", "monster_0", 2, "applier-a");
        assertEquals("apply_power", eff.kind);
        assertEquals("Vulnerable", eff.powerId);
        assertEquals("monster_0", eff.target);
        assertEquals(2, eff.amount);
        assertEquals("applier-a", eff.logicOwnerId);
    }

    @Test
    public void bashStyleMagicNumberMapsToApplyPowerNotMagicNumber() {
        Protocol.EffectDescription[] effects = ApplyPowerEffects.buildCardEffects(
            8, 0, 2, "Cultist", "player-a");
        assertEquals(2, effects.length);
        assertEquals("damage", effects[0].kind);
        assertEquals("apply_power", effects[1].kind);
        assertEquals("Vulnerable", effects[1].powerId);
        assertEquals("player-a", effects[1].logicOwnerId);
        assertEquals(2, effects[1].amount);
        assertEquals("Cultist", effects[1].target);
    }
}
