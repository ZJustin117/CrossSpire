package crossspire.reference;

import basemod.BaseMod;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import crossspire.CrossSpireMod;
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
        complete.seq = 1;
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
            list.add(pow);
        }
        return list.toArray(new Protocol.OperationStep[0]);
    }

    private Protocol.EffectDescription[] buildEffects(AbstractCard card, String targetId) {
        List<Protocol.EffectDescription> list = new ArrayList<>();
        if (card.baseDamage > 0) {
            Protocol.EffectDescription dmg = new Protocol.EffectDescription();
            dmg.kind = "damage";
            dmg.target = targetId;
            dmg.amount = card.baseDamage;
            list.add(dmg);
        }
        if (card.baseBlock > 0) {
            Protocol.EffectDescription blk = new Protocol.EffectDescription();
            blk.kind = "gain_block";
            blk.target = "self";
            blk.amount = card.baseBlock;
            list.add(blk);
        }
        if (card.magicNumber > 0) {
            Protocol.EffectDescription mgc = new Protocol.EffectDescription();
            mgc.kind = "magic_number";
            mgc.target = targetId;
            mgc.amount = card.magicNumber;
            list.add(mgc);
        }
        return list.toArray(new Protocol.EffectDescription[0]);
    }
}
