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
            BaseMod.logger.info("CapturePatches useCard: " + card.cardID + " connected=" + (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()));
            if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;
            if (CrossSpireMod.playerId.isEmpty()) return;

            Protocol.QueuePacket pkt = new Protocol.QueuePacket();
            pkt.packetId = CrossSpireMod.playerId + "/" + UUID.randomUUID().toString().substring(0, 8);
            pkt.senderId = CrossSpireMod.playerId;
            pkt.ownerId = CrossSpireMod.playerId;
            pkt.timestamp = System.currentTimeMillis();
            pkt.cardId = card.cardID;
            pkt.resourceHash = "";
            pkt.target = target != null ? target.id : "self";
            pkt.source = CrossSpireMod.playerId;
            pkt.seq = 1;

            CrossSpireMod.relayClient.send(Protocol.GSON.toJson(pkt));
            BaseMod.logger.info("CapturePatches queue_packet: " + card.cardID);
        }
    }
}
