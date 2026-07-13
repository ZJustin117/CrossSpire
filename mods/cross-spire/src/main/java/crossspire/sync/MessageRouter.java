package crossspire.sync;

import basemod.BaseMod;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;
import crossspire.combat.CombatResultReplayer;
import crossspire.combat.QueueManager;
import crossspire.network.Protocol;
import crossspire.reference.RemoteReference;
import crossspire.ui.QueueDisplay;
import crossspire.resource.RemoteResourceManager;
import crossspire.resource.ResourceRegistryTracker;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import java.util.ArrayList;
import java.util.List;

public class MessageRouter {

    private final SyncExecutor syncExecutor;
    private final QueueManager queueManager;
    private final CombatResultReplayer resultReplayer;

    public MessageRouter(SyncExecutor syncExecutor, QueueManager queueManager, CombatResultReplayer resultReplayer) {
        this.syncExecutor = syncExecutor;
        this.queueManager = queueManager;
        this.resultReplayer = resultReplayer;
    }

    public void route(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String type = msg.get("type").getAsString();

        if ("connected".equals(type) || "room_state".equals(type) || "player_joined".equals(type)) {
            return;
        }

        String subtype = msg.has("subtype") ? msg.get("subtype").getAsString() : "";

        if ("queue_packet".equals(type)) {
            BaseMod.logger.info("MessageRouter queue_packet routing...");
            try {
                Protocol.QueuePacket pkt = Protocol.GSON.fromJson(rawMessage, Protocol.QueuePacket.class);
                BaseMod.logger.info("MessageRouter queue_packet parsed: " + pkt.cardId + " by " + pkt.senderId.substring(0,8));
                queueManager.onQueuePacket(pkt);
                QueueDisplay.onPacket(pkt);
            } catch (Exception e) {
                BaseMod.logger.error("MessageRouter queue_packet parse error: " + e.getMessage());
            }
        } else if ("queue_complete".equals(type)) {
            Protocol.QueueComplete complete = Protocol.GSON.fromJson(rawMessage, Protocol.QueueComplete.class);
            queueManager.onQueueComplete(complete);
            QueueDisplay.onComplete(complete.packetId);
            resultReplayer.handleCombatResult(rawMessage);
        } else if ("player_state".equals(type)) {
            syncExecutor.handleSync("remote_player", null, 1, rawMessage);
        } else if ("stage_sync".equals(type)) {
            syncExecutor.handleSync(subtype.isEmpty() ? "battle_start" : subtype, null, 1, rawMessage);
        } else if ("state_sync".equals(type)) {
            syncExecutor.handleSync(subtype, null, 1, rawMessage);
        } else if ("resource_registry".equals(type)) {
            ResourceRegistryTracker.onRegistryReceived(rawMessage);
        } else if ("resource_request".equals(type)) {
            RemoteResourceManager.serveResource(rawMessage);
        } else if ("resource_response".equals(type)) {
            RemoteResourceManager.onResourceResponse(rawMessage);
        } else if ("hello".equals(type)) {
            CrossSpireMod.p2pManager.onHelloReceived(rawMessage);
        } else if ("player_ready".equals(type)) {
            CrossSpireMod.lobbyState.onPlayerReady(rawMessage);
        } else if ("invoke".equals(type)) {
            handleInvoke(rawMessage);
        } else if ("invoke_result".equals(type)) {
            Protocol.InvokeResultMessage result = Protocol.GSON.fromJson(rawMessage, Protocol.InvokeResultMessage.class);
            RemoteReference.onInvokeResult(result);
        } else if ("monster_intent".equals(type)) {
            syncExecutor.handleSync("monster_intent", null, 1, rawMessage);
        } else if ("combat_result".equals(type)) {
            syncExecutor.handleCombatResult(rawMessage);
        } else if ("event_result".equals(type)) {
            syncExecutor.handleEventResult(rawMessage);
        } else if ("reference_migrate".equals(type)) {
            handleReferenceMigrate(rawMessage);
        } else if ("reference_register".equals(type)) {
            Protocol.ReferenceRegisterMessage reg = Protocol.GSON.fromJson(rawMessage, Protocol.ReferenceRegisterMessage.class);
            BaseMod.logger.info("MessageRouter reference_register: " + reg.resourceType + ":" + reg.resourceId);
        }
    }

    public void replayCombatResult(Protocol.EffectDescription[] effects) {
        if (effects == null || effects.length == 0) return;
        BaseMod.logger.info("MessageRouter replayCombatResult: " + effects.length + " effects");
        resultReplayer.applyEffects(effects);
    }

    private void handleReferenceMigrate(String rawMessage) {
        Protocol.ReferenceMigrateMessage msg = Protocol.GSON.fromJson(rawMessage, Protocol.ReferenceMigrateMessage.class);
        BaseMod.logger.info("MessageRouter reference_migrate: " + msg.refId + " type=" + msg.resourceType);
        if (crossspire.reference.ContentValidator.matches(msg.resourceType, msg.resourceId, msg.resourceHash)) {
            Protocol.InvokeResultMessage reply = new Protocol.InvokeResultMessage();
            reply.type = "reference_migrate_ack";
            reply.source = CrossSpireMod.playerId;
            reply.target = msg.source;
            reply.seq = msg.seq;
            reply.refId = msg.refId;
            if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
                CrossSpireMod.relayClient.send(Protocol.GSON.toJson(reply));
                BaseMod.logger.info("MessageRouter sent reference_migrate_ack for: " + msg.refId);
            }
        }
    }

    private void handleInvoke(String rawMessage) {
        Protocol.InvokeMessage inv = Protocol.GSON.fromJson(rawMessage, Protocol.InvokeMessage.class);
        BaseMod.logger.info("MessageRouter invoke: " + inv.refId + " trigger=" + inv.trigger);

        if (AbstractDungeon.player == null || AbstractDungeon.actionManager == null) {
            BaseMod.logger.info("MessageRouter invoke skipped: not in combat");
            return;
        }

        String cardId = inv.refId.contains("@") ? inv.refId.split(":")[1].split("@")[0] : "unknown";
        String targetId = inv.args != null ? inv.args : "self";

        AbstractCard template = CardLibrary.getCard(cardId);
        if (template == null) {
            BaseMod.logger.info("MessageRouter invoke: card not found " + cardId);
            return;
        }

        AbstractCard copy = template.makeCopy();
        AbstractCreature target = AbstractDungeon.player;
        if (!"self".equals(targetId) && AbstractDungeon.getCurrRoom() != null) {
            AbstractMonster m = AbstractDungeon.getCurrRoom().monsters.getMonster(targetId);
            if (m != null) target = m;
        }
        AbstractDungeon.actionManager.addToBottom(new UseCardAction(copy, target));

        Protocol.InvokeResultMessage result = new Protocol.InvokeResultMessage();
        result.source = CrossSpireMod.playerId;
        result.seq = 1;
        result.refId = inv.refId;
        result.effects = buildInvokeEffects(template, targetId);
        result.operationSequence = new Protocol.OperationStep[0];

        if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            CrossSpireMod.relayClient.send(Protocol.GSON.toJson(result));
            BaseMod.logger.info("MessageRouter sent invoke_result: " + inv.refId);
        }
    }

    private Protocol.EffectDescription[] buildInvokeEffects(AbstractCard card, String targetId) {
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
