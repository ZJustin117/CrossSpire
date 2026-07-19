package crossspire.reference;

import basemod.BaseMod;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import crossspire.CrossSpireMod;
import crossspire.combat.ApplyPowerEffects;
import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LocalReference<T> extends Reference<T> {

    public LocalReference(String cardId, String ownerId) {
        super("card:" + cardId + "@" + ownerId, ownerId, Type.LOCAL, "");
    }

    @Override
    public void dereference(Object... args) {
        String cardId = refId.split(":")[1].split("@")[0];
        String targetId = args.length > 0 ? (String) args[0] : "self";

        BaseMod.logger.info("LocalReference dereference: " + cardId + " -> " + targetId);

        if (AbstractDungeon.player == null || AbstractDungeon.actionManager == null) {
            BaseMod.logger.info("LocalReference skipped: not in combat");
            return;
        }

        AbstractCard template = CardLibrary.getCard(cardId);
        if (template == null) {
            BaseMod.logger.info("LocalReference card not found: " + cardId);
            return;
        }

        AbstractCard copy = template.makeCopy();
        AbstractCreature target = AbstractDungeon.player;
        if (!"self".equals(targetId) && AbstractDungeon.getCurrRoom() != null) {
            AbstractMonster m = AbstractDungeon.getCurrRoom().monsters.getMonster(targetId);
            if (m != null) target = m;
        }

        AbstractDungeon.actionManager.addToBottom(new UseCardAction(copy, target));

        Protocol.EffectDescription[] captured = buildEffects(template, targetId);

        Protocol.QueueComplete complete = new Protocol.QueueComplete();
        complete.source = ownerId;
        complete.executorId = ownerId;
        complete.seq = CrossSpireMod.nextSeq();
        complete.type = "combat_result";
        complete.packetId = refId + "/" + UUID.randomUUID().toString().substring(0, 8);
        complete.effects = captured;
        complete.operationSequence = buildVfxOps(copy, targetId);

        if (CrossSpireMod.isConnected()) {
            CrossSpireMod.send(Protocol.GSON.toJson(complete));
            StringBuilder fx = new StringBuilder();
            for (int i = 0; i < captured.length; i++) {
                if (i > 0) fx.append(", ");
                fx.append(captured[i].kind).append("=").append(captured[i].amount);
            }
            BaseMod.logger.info("LocalReference queue_complete: " + cardId + " [" + fx + "]");
        }
    }

    private Protocol.OperationStep[] buildVfxOps(AbstractCard card, String targetId) {
        List<Protocol.OperationStep> list = new ArrayList<>();

        Protocol.OperationStep playCard = new Protocol.OperationStep();
        playCard.step = "play_card";
        playCard.cardId = card.cardID;
        playCard.source = ownerId;
        playCard.target = targetId;
        playCard.cardType = card.type.name();
        playCard.cardRarity = card.rarity.name();
        playCard.cardTarget = card.target.name();
        list.add(playCard);

        boolean isAttack = card.baseDamage > 0 && card.type == AbstractCard.CardType.ATTACK;
        if (isAttack) {
            Protocol.OperationStep atk = new Protocol.OperationStep();
            atk.step = "vfx";
            atk.cardId = card.cardID;
            atk.target = targetId;
            atk.vfxKind = "ATTACK";
            list.add(atk);
        }
        if (card.baseBlock > 0) {
            Protocol.OperationStep blk = new Protocol.OperationStep();
            blk.step = "vfx";
            blk.cardId = card.cardID;
            blk.target = "self";
            blk.vfxKind = "BLOCK";
            list.add(blk);
        }
        if (card.magicNumber > 0) {
            Protocol.OperationStep pow = new Protocol.OperationStep();
            pow.step = "apply_power";
            pow.powerId = "Vulnerable";
            pow.target = targetId;
            pow.amount = card.magicNumber;
            pow.logicOwnerId = ownerId;
            list.add(pow);
        }
        return list.toArray(new Protocol.OperationStep[0]);
    }

    private Protocol.EffectDescription[] buildEffects(AbstractCard card, String targetId) {
        return ApplyPowerEffects.buildCardEffects(
            card.baseDamage, card.baseBlock, card.magicNumber, targetId, ownerId);
    }

    /** @deprecated use {@link ApplyPowerEffects#applyPowerEffect} */
    public static Protocol.EffectDescription applyPowerEffect(
            String powerId, String targetId, int amount, String applierId) {
        return ApplyPowerEffects.applyPowerEffect(powerId, targetId, amount, applierId);
    }
}
