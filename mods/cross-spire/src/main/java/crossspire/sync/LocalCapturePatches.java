package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;
import crossspire.network.Protocol;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
public class LocalCapturePatches {

    private static final AtomicInteger suppressDepth = new AtomicInteger(0);

    public static void pushSuppress() {
        suppressDepth.incrementAndGet();
    }

    @SpirePatch(clz = AbstractPlayer.class, method = "useCard", paramtypez = {AbstractCard.class, AbstractMonster.class, int.class})
    public static class OnUseCard {
        @SpirePostfixPatch
        public static void Postfix(AbstractPlayer __instance, AbstractCard card, AbstractMonster target, int energyOnUse) {
            if (!CrossSpireMod.isConnected()) return;
            if (EventSuppression.isSuppressed()) return;

            if (suppressDepth.get() > 0) {
                suppressDepth.decrementAndGet();
                return;
            }

            String targetId = target != null ? target.id : "self";
            Protocol.QueueSubmitMessage pkt = QueueSubmitBuilder.build(card.cardID, targetId);

            CrossSpireMod.send(Protocol.GSON.toJson(pkt));
            BaseMod.logger.info("CapturePatches queue_submit: " + card.cardID + " to host="
                + CrossSpireMod.hostId.substring(0, 8));
        }
    }
}
