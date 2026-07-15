package crossspire;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

@SuppressWarnings("unused")
public class SuppressBaseModPatches {

    @SpirePatch(clz = BaseMod.class, method = "publishPostBattle", paramtypez = {AbstractRoom.class})
    public static class SuppressPostBattle {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(AbstractRoom __battleRoom) {
            if (EventSuppression.isSuppressed()) return SpireReturn.Return(null);
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = BaseMod.class, method = "publishOnPlayerDamaged", paramtypez = {int.class, DamageInfo.class})
    public static class SuppressOnPlayerDamaged {
        @SpirePrefixPatch
        public static SpireReturn<Integer> Prefix(int __amount, DamageInfo __info) {
            if (EventSuppression.isSuppressed()) return SpireReturn.Return(__amount);
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = BaseMod.class, method = "publishOnPlayerLoseBlock", paramtypez = {int.class})
    public static class SuppressOnPlayerLoseBlock {
        @SpirePrefixPatch
        public static SpireReturn<Integer> Prefix(int __amount) {
            if (EventSuppression.isSuppressed()) return SpireReturn.Return(__amount);
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = BaseMod.class, method = "publishRelicGet", paramtypez = {AbstractRelic.class})
    public static class SuppressRelicGet {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(AbstractRelic __relic) {
            if (EventSuppression.isSuppressed()) return SpireReturn.Return(null);
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = BaseMod.class, method = "publishPotionGet", paramtypez = {AbstractPotion.class})
    public static class SuppressPotionGet {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(AbstractPotion __potion) {
            if (EventSuppression.isSuppressed()) return SpireReturn.Return(null);
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = BaseMod.class, method = "publishPostPotionUse", paramtypez = {AbstractPotion.class})
    public static class SuppressPostPotionUse {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(AbstractPotion __potion) {
            if (EventSuppression.isSuppressed()) return SpireReturn.Return(null);
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = BaseMod.class, method = "publishOnCardUse", paramtypez = {AbstractCard.class})
    public static class SuppressOnCardUse {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(AbstractCard __card) {
            if (EventSuppression.isSuppressed()) return SpireReturn.Return(null);
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = BaseMod.class, method = "publishPostPowerApply", paramtypez = {AbstractPower.class, AbstractCreature.class, AbstractCreature.class})
    public static class SuppressPostPowerApply {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(AbstractPower __power, AbstractCreature __source, AbstractCreature __target) {
            if (EventSuppression.isSuppressed()) return SpireReturn.Return(null);
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = BaseMod.class, method = "publishPostDraw", paramtypez = {AbstractCard.class})
    public static class SuppressPostDraw {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(AbstractCard __card) {
            if (EventSuppression.isSuppressed()) return SpireReturn.Return(null);
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = BaseMod.class, method = "publishPostExhaust", paramtypez = {AbstractCard.class})
    public static class SuppressPostExhaust {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(AbstractCard __card) {
            if (EventSuppression.isSuppressed()) return SpireReturn.Return(null);
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = BaseMod.class, method = "publishOnPlayerTurnStart", paramtypez = {})
    public static class SuppressOnPlayerTurnStart {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix() {
            if (EventSuppression.isSuppressed()) return SpireReturn.Return(null);
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = BaseMod.class, method = "publishPostEnergyRecharge", paramtypez = {})
    public static class SuppressPostEnergyRecharge {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix() {
            if (EventSuppression.isSuppressed()) return SpireReturn.Return(null);
            return SpireReturn.Continue();
        }
    }
}
