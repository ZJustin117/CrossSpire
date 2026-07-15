package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import crossspire.reference.ContentValidator;

@SuppressWarnings("unused")
public class LocalCapturePatches {

    @SpirePatch(clz = AbstractPlayer.class, method = "useCard", paramtypez = {AbstractCard.class, AbstractMonster.class, int.class})
    public static class OnUseCard {
        @SpirePostfixPatch
        public static void Postfix(AbstractPlayer __instance, AbstractCard card, AbstractMonster target, int energyOnUse) {
            if (!CrossSpireMod.isConnected()) return;
            if (CrossSpireMod.playerId.isEmpty() || CrossSpireMod.hostId.isEmpty()) return;

            Protocol.QueueSubmitMessage pkt = new Protocol.QueueSubmitMessage();
            pkt.senderId = CrossSpireMod.playerId;
            pkt.ownerId = CrossSpireMod.playerId;
            pkt.cardId = card.cardID;
            pkt.resourceHash = ContentValidator.hashResource("card", card.cardID);
            pkt.gameTarget = target != null ? target.id : "self";
            pkt.timestamp = System.currentTimeMillis();
            pkt.source = CrossSpireMod.playerId;
            pkt.seq = CrossSpireMod.nextSeq();
            pkt.target = CrossSpireMod.hostId;

            CrossSpireMod.send(Protocol.GSON.toJson(pkt));
            BaseMod.logger.info("CapturePatches queue_submit: " + card.cardID + " to host="
                + CrossSpireMod.hostId.substring(0, 8));
        }
    }
}
