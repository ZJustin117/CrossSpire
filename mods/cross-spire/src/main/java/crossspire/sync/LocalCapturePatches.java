package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import java.util.UUID;

@SuppressWarnings("unused")
public class LocalCapturePatches {

    @SpirePatch(clz = AbstractPlayer.class, method = "useCard", paramtypez = {AbstractCard.class, AbstractMonster.class, int.class})
    public static class OnUseCard {
        @SpirePostfixPatch
        public static void Postfix(AbstractPlayer __instance, AbstractCard card, AbstractMonster target, int energyOnUse) {
            if (CrossSpireMod.relayClient == null) return;

            Protocol.QueueSubmit submit = new Protocol.QueueSubmit();
            submit.source = CrossSpireMod.playerId.isEmpty() ? "unknown" : CrossSpireMod.playerId;
            submit.seq = 1;
            submit.cardId = card.cardID;
            submit.upgraded = card.upgraded;
            submit.target = target != null ? target.id : "self";
            submit.energyCost = energyOnUse;

            String json = Protocol.GSON.toJson(submit);
            CrossSpireMod.relayClient.send(json);
            BaseMod.logger.info("CapturePatches queue_submit: " + json);
        }
    }
}
