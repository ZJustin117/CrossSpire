package crossspire.reference;

import basemod.BaseMod;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;
import crossspire.network.Protocol;
import java.util.UUID;

public class LocalReference<T> extends Reference<T> {

    public LocalReference(String cardId, String ownerId) {
        super("card:" + cardId + "@" + ownerId, ownerId, Type.LOCAL, "");
    }

    @Override
    public void dereference(Object... args) {
        String cardId = refId.split(":")[1].split("@")[0];
        String targetId = args.length > 0 ? (String) args[0] : "self";

        BaseMod.logger.info("LocalReference dereference: " + cardId + " → " + targetId);

        AbstractCard card = CardLibrary.getCard(cardId);
        if (card == null) {
            BaseMod.logger.info("LocalReference card not found: " + cardId);
            return;
        }

        EventSuppression.suppressEvents(() -> {
            AbstractCard copy = card.makeCopy();
            if (AbstractDungeon.player != null) {
                AbstractCreature target = AbstractDungeon.player;
                if (!"self".equals(targetId) && AbstractDungeon.getCurrRoom() != null) {
                    AbstractMonster m = AbstractDungeon.getCurrRoom().monsters.getMonster(targetId);
                    if (m != null) target = m;
                }
                AbstractDungeon.actionManager.addToBottom(new UseCardAction(copy, target));
            }
        });

        Protocol.EffectDescription dmg = new Protocol.EffectDescription();
        dmg.kind = "damage";
        dmg.target = targetId;
        dmg.amount = 6;

        Protocol.QueueComplete complete = new Protocol.QueueComplete();
        complete.source = ownerId;
        complete.seq = 1;
        complete.packetId = refId + "/" + UUID.randomUUID().toString().substring(0, 8);
        complete.effects = new Protocol.EffectDescription[] { dmg };
        complete.operationSequence = new Protocol.OperationStep[0];

        if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            CrossSpireMod.relayClient.send(Protocol.GSON.toJson(complete));
            BaseMod.logger.info("LocalReference broadcast queue_complete: " + cardId);
        }
    }
}
