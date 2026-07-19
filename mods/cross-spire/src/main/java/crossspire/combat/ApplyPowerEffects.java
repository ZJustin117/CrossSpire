package crossspire.combat;

import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure helpers for apply_power effect construction (no STS class load at link time for tests).
 */
public final class ApplyPowerEffects {

    private ApplyPowerEffects() {}

    /** Build apply_power effect with applier-first logic_owner_id. */
    public static Protocol.EffectDescription applyPowerEffect(
            String powerId, String targetId, int amount, String applierId) {
        Protocol.EffectDescription eff = new Protocol.EffectDescription();
        eff.kind = "apply_power";
        eff.powerId = powerId;
        eff.target = targetId;
        eff.amount = amount;
        eff.logicOwnerId = applierId;
        return eff;
    }

    /**
     * Bash-style mapping: baseDamage / baseBlock / magicNumber (→ Vulnerable apply_power).
     * Used by LocalReference and unit tests without AbstractCard.
     */
    public static Protocol.EffectDescription[] buildCardEffects(
            int baseDamage, int baseBlock, int magicNumber, String targetId, String ownerId) {
        List<Protocol.EffectDescription> list = new ArrayList<>();
        if (baseDamage > 0) {
            Protocol.EffectDescription dmg = new Protocol.EffectDescription();
            dmg.kind = "damage";
            dmg.target = targetId;
            dmg.amount = baseDamage;
            list.add(dmg);
        }
        if (baseBlock > 0) {
            Protocol.EffectDescription blk = new Protocol.EffectDescription();
            blk.kind = "gain_block";
            blk.target = "self";
            blk.amount = baseBlock;
            list.add(blk);
        }
        if (magicNumber > 0) {
            list.add(applyPowerEffect("Vulnerable", targetId, magicNumber, ownerId));
        }
        return list.toArray(new Protocol.EffectDescription[0]);
    }
}
