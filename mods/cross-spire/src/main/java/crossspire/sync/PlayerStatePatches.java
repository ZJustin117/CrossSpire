package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;

@SuppressWarnings("unused")
public class PlayerStatePatches {

    @SpirePatch(clz = AbstractPlayer.class, method = "damage", paramtypez = {DamageInfo.class})
    public static class OnDamage {
        @SpirePostfixPatch
        public static void Postfix(AbstractPlayer __instance) {
            BaseMod.logger.info("PlayerStatePatches.OnDamage: suppressed=" + EventSuppression.isSuppressed() + " connected=" + CrossSpireMod.isConnected());
            if (EventSuppression.isSuppressed()) return;
            if (!CrossSpireMod.isConnected()) return;
            PlayerStateBroadcaster.broadcast(__instance);
        }
    }
}
