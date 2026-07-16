package crossspire.sync;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;
import crossspire.combat.CombatResultReplayer;
import crossspire.combat.CentralQueueManager;
import crossspire.network.HeartbeatManager;
import crossspire.network.PacketOperation;
import crossspire.network.Protocol;
import crossspire.network.StageVoteSender;
import crossspire.network.StandardPacket;
import crossspire.reference.RemoteReference;
import crossspire.ui.QueueDisplay;
import crossspire.ui.RemoteEventDisplay;
import crossspire.resource.RemoteResourceManager;
import crossspire.resource.ResourceRegistryTracker;
import crossspire.resource.RemoteAssetCache;
import crossspire.resource.RemoteCharacterResource;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.remote.RemotePlayerState;
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
    private final CentralQueueManager centralQueue;
    private final CombatResultReplayer resultReplayer;
    private static String eventSelectionRequester = null;

    public MessageRouter(SyncExecutor syncExecutor, CentralQueueManager centralQueue, CombatResultReplayer resultReplayer) {
        this.syncExecutor = syncExecutor;
        this.centralQueue = centralQueue;
        this.resultReplayer = resultReplayer;
    }

    public void route(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);

        // StandardPacket detection: if JSON has "operation" field, it's a new-format packet
        if (msg.has("operation")) {
            routeStandardPacket(rawMessage);
            return;
        }

        String type = msg.get("type").getAsString();
        String source = msg.has("source") ? msg.get("source").getAsString() : "";

        if ("connected".equals(type)) {
            return;
        }

        if ("room_state".equals(type)) {
            handleRoomState(msg);
            return;
        }

        if ("player_joined".equals(type)) {
            handlePlayerJoined(msg);
            return;
        }

        if ("player_left".equals(type)) {
            handlePlayerLeft(msg);
            return;
        }
        if ("ping".equals(type)) {
            JsonObject pong = new JsonObject();
            pong.addProperty("type", "pong");
            pong.addProperty("source", CrossSpireMod.playerId);
            pong.addProperty("seq", CrossSpireMod.nextSeq());
            CrossSpireMod.send(pong.toString());
            crossspire.network.HeartbeatManager.handlePong(source);
            return;
        }
        if ("pong".equals(type)) {
            crossspire.network.HeartbeatManager.handlePong(source);
            return;
        }
        if ("host_migration".equals(type)) {
            String newHost = msg.has("new_host") ? msg.get("new_host").getAsString() : "";
            BaseMod.logger.info("MessageRouter host_migration → new host=" + newHost.substring(0, 8));
            return;
        }

        String subtype = msg.has("subtype") ? msg.get("subtype").getAsString() : "";

        if ("combat_result".equals(type)) {
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
            CrossSpireMod.connectionManager.onHelloReceived(rawMessage);
        } else if ("player_ready".equals(type)) {
            CrossSpireMod.lobbyState.onPlayerReady(rawMessage);
        } else if ("invoke".equals(type)) {
            handleInvoke(rawMessage);
        } else if ("invoke_result".equals(type)) {
            BaseMod.logger.info("MessageRouter invoke_result received: " + rawMessage.substring(0, Math.min(120, rawMessage.length())));
            Protocol.InvokeResultMessage result = Protocol.GSON.fromJson(rawMessage, Protocol.InvokeResultMessage.class);
            RemoteReference.onInvokeResult(result);
            if (CrossSpireMod.isRoomHost()) {
                CrossSpireMod.centralQueueManager.onInvokeResult(result);
            }
            // Host broadcasts combat_result so all clients can INDUCED replay
            if (CrossSpireMod.isRoomHost()) {
                broadcastCombatResult(result);
            }
        } else if ("monster_intent".equals(type)) {
            syncExecutor.handleSync("monster_intent", null, 1, rawMessage);
        } else if ("event_result".equals(type)) {
            syncExecutor.handleEventResult(rawMessage);
            RemoteEventDisplay.hide();
        } else if ("event_select".equals(type)) {
            handleEventSelect(rawMessage, source);
        } else if ("event_interface".equals(type)) {
            JsonObject ei = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
            String name = ei.has("event_id") ? ei.get("event_id").getAsString() : "?";
            int opts = ei.has("options") ? ei.getAsJsonArray("options").size() : 0;
            BaseMod.logger.info("MessageRouter event_interface: " + name + " options=" + opts);
            if (!CrossSpireMod.stageHost.isStageHost()) {
                String desc = ei.has("description") ? ei.get("description").getAsString() : "";
                JsonArray options = ei.has("options") ? ei.getAsJsonArray("options") : new JsonArray();
                RemoteEventDisplay.show(name, desc, options);
            }
        } else if ("reference_migrate".equals(type)) {
            Protocol.ReferenceRegisterMessage reg = Protocol.GSON.fromJson(rawMessage, Protocol.ReferenceRegisterMessage.class);
            BaseMod.logger.info("MessageRouter reference_register: " + reg.resourceType + ":" + reg.resourceId);
        } else if ("full_snapshot".equals(type)) {
            syncExecutor.handleFullSnapshot(rawMessage);
        } else if ("stage_host_result".equals(type)) {
            handleStageHostResult(msg);
        } else if ("stage_vote".equals(type)) {
            handleStageVoteJson(msg);
        } else if ("stage_host_election".equals(type)) {
            BaseMod.logger.info("MessageRouter " + type + " (reserved, not yet implemented)");
        } else if ("animation_sync".equals(type)) {
            handleAnimationSync(rawMessage);
        } else if ("player_end_turn".equals(type)) {
            handlePlayerEndTurn();
        } else if ("queue_submit".equals(type)) {
            handleQueueSubmit(rawMessage);
            QueueDisplay.resetEndTurn();
        } else if ("queue_update".equals(type)) {
            Protocol.QueueUpdateMessage upd = Protocol.GSON.fromJson(rawMessage, Protocol.QueueUpdateMessage.class);
            QueueDisplay.onUpdate(upd.entries);
        } else if ("queue_empty".equals(type)) {
            QueueDisplay.onQueueEmpty();
            Gdx.app.postRunnable(new Runnable() {
                @Override public void run() {
                    if (AbstractDungeon.overlayMenu != null
                        && AbstractDungeon.overlayMenu.endTurnButton != null) {
                        AbstractDungeon.overlayMenu.endTurnButton.enable();
                    }
                }
            });
        } else if ("room_pin".equals(type)) {
            handleRoomPin(rawMessage);
        } else if ("room_consensus".equals(type)) {
            JsonObject cm = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
            int room = cm.has("room") ? cm.get("room").getAsInt() : -1;
            BaseMod.logger.info("MessageRouter room_consensus: room=" + room);
            if (room >= 0) {
                SyncExecutor.executeRoomConsensus(room);
            }
        } else if ("interact_request".equals(type)) {
            handleInteractRequest(rawMessage);
        } else if ("interact_response".equals(type)) {
            handleInteractResponse(rawMessage);
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
            if (CrossSpireMod.isConnected()) {
                CrossSpireMod.send(Protocol.GSON.toJson(reply));
                BaseMod.logger.info("MessageRouter sent reference_migrate_ack for: " + msg.refId);
            }
        }
    }

    private void handleInvoke(String rawMessage) {
        Protocol.InvokeMessage inv = Protocol.GSON.fromJson(rawMessage, Protocol.InvokeMessage.class);
        BaseMod.logger.info("MessageRouter invoke: " + inv.refId + " trigger=" + inv.trigger
            + " target=" + (inv.target != null ? inv.target.substring(0, 8) : "-"));

        if (inv.target != null && !inv.target.equals(CrossSpireMod.playerId)) {
            if (CrossSpireMod.isRoomHost()) {
                String owner = extractOwnerFromRef(inv.refId);
                if (!owner.equals(CrossSpireMod.playerId)) {
                    inv.target = owner;
                    CrossSpireMod.send(Protocol.GSON.toJson(inv));
                    BaseMod.logger.info("MessageRouter forwarded invoke to owner=" + owner.substring(0, 8));
                }
            } else {
                BaseMod.logger.info("MessageRouter invoke not for us: target=" + inv.target.substring(0, 8));
            }
            return;
        }

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
        LocalCapturePatches.pushSuppress();
        AbstractDungeon.actionManager.addToBottom(new UseCardAction(copy, target));

        Protocol.InvokeResultMessage result = new Protocol.InvokeResultMessage();
        result.source = CrossSpireMod.playerId;
        result.target = inv.source;
        result.seq = CrossSpireMod.nextSeq();
        result.refId = inv.refId;
        result.effects = buildInvokeEffects(template, targetId);
        result.operationSequence = new Protocol.OperationStep[0];

        if (CrossSpireMod.isConnected()) {
            CrossSpireMod.send(Protocol.GSON.toJson(result));
            StringBuilder fx = new StringBuilder();
            Protocol.EffectDescription[] effs = result.effects;
            for (int i = 0; i < effs.length; i++) {
                if (i > 0) fx.append(", ");
                fx.append(effs[i].kind).append("=").append(effs[i].amount);
            }
            BaseMod.logger.info("MessageRouter sent invoke_result: " + inv.refId + " [" + fx + "]");
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

    private void handlePlayerEndTurn() {
        String source = CrossSpireMod.playerId;
        if (AbstractDungeon.player == null || AbstractDungeon.overlayMenu == null) return;
        com.megacrit.cardcrawl.ui.buttons.EndTurnButton btn = AbstractDungeon.overlayMenu.endTurnButton;
        if (btn != null && btn.enabled) {
            BaseMod.logger.info("MessageRouter player_end_turn: ending turn");
            EndTurnSyncPatches.suppressEndTurn = true;
            AbstractDungeon.overlayMenu.endTurnButton.disable(true);
            EndTurnSyncPatches.suppressEndTurn = false;
        }
    }

    private void handleQueueSubmit(String rawMessage) {
        if (!CrossSpireMod.isRoomHost()) return;
        Protocol.QueueSubmitMessage pkt = Protocol.GSON.fromJson(rawMessage, Protocol.QueueSubmitMessage.class);
        CrossSpireMod.centralQueueManager.onQueueSubmit(pkt);
    }

    private void broadcastCombatResult(Protocol.InvokeResultMessage result) {
        Protocol.CombatResultMessage broadcast = new Protocol.CombatResultMessage();
        broadcast.source = CrossSpireMod.playerId;
        broadcast.seq = CrossSpireMod.nextSeq();
        broadcast.effects = result.effects;
        broadcast.operationSequence = result.operationSequence;

        if (CrossSpireMod.isConnected()) {
            CrossSpireMod.send(Protocol.GSON.toJson(broadcast));
            BaseMod.logger.info("MessageRouter broadcast combat_result effects="
                + (result.effects != null ? result.effects.length : 0));
        }
    }

    private void handleAnimationSync(String rawMessage) {
        Protocol.AnimationSyncMessage msg = Protocol.GSON.fromJson(rawMessage, Protocol.AnimationSyncMessage.class);
        String playerId = msg.playerId;
        String anim = msg.animationName;

        if (playerId == null || anim == null) return;

        RemotePlayerState rp = RemotePlayerRegistry.get(playerId);
        if (rp == null) return;

        rp.currentAnimation = anim;
        RemoteCharacterResource chr = rp.getCharacterResource();
        if (chr != null && chr.isLoaded()) {
            chr.setAnimation(anim, true);
            BaseMod.logger.info("MessageRouter animation_sync: " + playerId.substring(0, 8)
                + " -> " + anim);
        }
    }

    private void handleRoomState(JsonObject msg) {
        if (msg.has("host") && !msg.get("host").isJsonNull()) {
            CrossSpireMod.hostId = msg.get("host").getAsString();
            HeartbeatManager.handlePong(CrossSpireMod.hostId);
            BaseMod.logger.info("MessageRouter room_state host: " + CrossSpireMod.hostId.substring(0, 8));
        }
        if (msg.has("players")) {
            com.google.gson.JsonArray players = msg.getAsJsonArray("players");
            CrossSpireMod.lobbyState.onRoomJoined(players.size());
            RemotePlayerRegistry.clear();
            for (int i = 0; i < players.size(); i++) {
                String pid = players.get(i).getAsString();
                if (!pid.equals(CrossSpireMod.playerId)) {
                    RemotePlayerRegistry.register(pid);
                    CrossSpireMod.lobbyState.onPlayerJoined(pid);
                }
            }
        }
        String code = msg.has("code") ? msg.get("code").getAsString() : "";
        CrossSpireMod.lobbyScreen.setStatus("In room " + code);
        ResourceRegistryTracker.sendMyRegistry();
    }

    private void handlePlayerJoined(JsonObject msg) {
        String joinedId = msg.get("playerId").getAsString();
        BaseMod.logger.info("MessageRouter player_joined: " + joinedId.substring(0, 8));
        RemotePlayerRegistry.register(joinedId);
        CrossSpireMod.lobbyState.onPlayerJoined(joinedId);
    }

    private void handlePlayerLeft(JsonObject msg) {
        String leftId = msg.get("playerId").getAsString();
        BaseMod.logger.info("MessageRouter player_left: " + leftId.substring(0, 8));
        RemotePlayerRegistry.remove(leftId);
        onPlayerLeft(leftId);
    }

    public void onPlayerLeft(String playerId) {
        BaseMod.logger.info("MessageRouter onPlayerLeft: " + playerId.substring(0, 8));
        CrossSpireMod.lobbyState.onPlayerLeft(playerId);
        RemoteAssetCache.clearCharacters();
    }

    private static String extractOwnerFromRef(String refId) {
        if (refId == null) return "";
        int atIdx = refId.lastIndexOf('@');
        if (atIdx < 0) return "";
        String after = refId.substring(atIdx + 1);
        int slashIdx = after.indexOf('/');
        return slashIdx > 0 ? after.substring(0, slashIdx) : after;
    }

    public void handleStageVote(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        handleStageVoteJson(msg);
    }

    private void handleStageVoteJson(JsonObject msg) {
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        String candidate = msg.has("candidate") ? msg.get("candidate").getAsString() : "";
        if (source.isEmpty() || candidate.isEmpty()) return;

        if (!CrossSpireMod.isRoomHost()) {
            BaseMod.logger.info("MessageRouter stage_vote from " + source.substring(0, 8) + " → " + candidate.substring(0, 8));
            return;
        }

        if (CrossSpireMod.roomHost != null) {
            CrossSpireMod.roomHost.castVote(source, candidate);
            String result = CrossSpireMod.roomHost.checkStageVoteConsensus();
            if (result != null) {
                BaseMod.logger.info("MessageRouter stage_vote consensus: " + result.substring(0, 8));
                String hostMsg = StageVoteSender.buildStageHostResult(CrossSpireMod.playerId, result);
                CrossSpireMod.send(hostMsg);
                CrossSpireMod.stageHost.setStageHost(result);
            } else {
                String votesJson = CrossSpireMod.roomHost.getVotesJson();
                String votesMsg = StageVoteSender.buildStageVotes(CrossSpireMod.playerId, votesJson);
                CrossSpireMod.send(votesMsg);
            }
        }
    }

    private void handleStageHostResult(JsonObject msg) {
        String hostId = msg.has("host_id") ? msg.get("host_id").getAsString() : "";
        if (hostId.isEmpty()) return;
        BaseMod.logger.info("MessageRouter stage_host_result: " + hostId.substring(0, 8));
        if (CrossSpireMod.stageHost != null) {
            CrossSpireMod.stageHost.setStageHost(hostId);
        }
    }

    public void handleRoomPin(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        int roomIndex = msg.has("room") ? msg.get("room").getAsInt() : -1;
        if (source.isEmpty() || roomIndex < 0) return;

        if (!CrossSpireMod.isRoomHost()) {
            BaseMod.logger.info("MessageRouter room_pin from " + source.substring(0, 8) + " room=" + roomIndex);
            return;
        }

        if (CrossSpireMod.roomHost != null) {
            CrossSpireMod.roomHost.pinRoom(source, roomIndex);
            int consensus = CrossSpireMod.roomHost.checkConsensus();
            if (consensus >= 0) {
                BaseMod.logger.info("MessageRouter room_pin consensus: room=" + consensus);
                SyncExecutor.executeRoomConsensus(consensus);
            } else {
                String pinsJson = CrossSpireMod.roomHost.getPinsJson();
                String pinsMsg = crossspire.network.RoomPinSender.buildRoomPins(
                    CrossSpireMod.playerId, pinsJson);
                CrossSpireMod.send(pinsMsg);
            }
        }
    }

    private void routeStandardPacket(String rawMessage) {
        StandardPacket pkt = StandardPacket.fromJson(rawMessage);
        String op = pkt.operation;
        JsonObject payload = pkt.payload != null ? pkt.payload : new JsonObject();

        // Add source/seq into payload for handlers that read them
        if (!payload.has("source")) payload.addProperty("source", pkt.source);

        if (PacketOperation.QUEUE_SUBMIT.equals(op)) {
            Protocol.QueueSubmitMessage qsm = Protocol.GSON.fromJson(payload.toString(), Protocol.QueueSubmitMessage.class);
            handleQueueSubmit(Protocol.GSON.toJson(qsm));
        } else if (PacketOperation.INVOKE.equals(op)) {
            String raw = Protocol.GSON.toJson(payload);
            // compatibility — old handler uses InvokeMessage format
            JsonObject invokeObj = new JsonObject();
            invokeObj.addProperty("type", "invoke");
            invokeObj.addProperty("source", pkt.source);
            invokeObj.add("args", payload.get("args"));
            invokeObj.add("ref_id", payload.get("ref_id"));
            invokeObj.addProperty("trigger", payload.has("trigger") ? payload.get("trigger").getAsString() : "");
            invokeObj.addProperty("target", pkt.ownerId);
            handleInvoke(Protocol.GSON.toJson(invokeObj));
        } else {
            BaseMod.logger.info("MessageRouter stdpkt: " + op + " (not yet routed)");
        }
    }

    private void handleEventSelect(String rawMessage, String source) {
        JsonObject sel = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        int idx = sel.has("option_index") ? sel.get("option_index").getAsInt() : -1;

        if (CrossSpireMod.stageHost != null && CrossSpireMod.stageHost.isStageHost()) {
            BaseMod.logger.info("MessageRouter event_select: option=" + idx + " from " + source.substring(0, 8));
            eventSelectionRequester = source;
            if (AbstractDungeon.getCurrRoom() != null
                && AbstractDungeon.getCurrRoom().event != null
                && idx >= 0) {
                try {
                    java.lang.reflect.Method m = AbstractDungeon.getCurrRoom().event.getClass()
                        .getMethod("buttonEffect", int.class);
                    m.setAccessible(true);
                    m.invoke(AbstractDungeon.getCurrRoom().event, idx);
                } catch (Exception ex) {
                    BaseMod.logger.error("MessageRouter event_select invoke failed: " + ex.getMessage());
                }
            }
        } else {
            CrossSpireMod.send((String) rawMessage);
        }
    }

    public void handleInteractRequest(String rawMessage) {
        JsonObject ir = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String caller = ir.has("source") ? ir.get("source").getAsString() : "";
        BaseMod.logger.info("MessageRouter interact_request from " + caller.substring(0, 8));

        if (CrossSpireMod.isRoomHost() && eventSelectionRequester != null
                && !eventSelectionRequester.equals(CrossSpireMod.playerId)) {
            CrossSpireMod.connectionManager.send(eventSelectionRequester, rawMessage);
            BaseMod.logger.info("MessageRouter forwarded interact_request to "
                + eventSelectionRequester.substring(0, 8));
        } else if (!CrossSpireMod.stageHost.isStageHost()) {
            receiveInteractRequest(ir);
        }
    }

    private void receiveInteractRequest(JsonObject ir) {
        if (!CrossSpireMod.isConnected()) return;
        if (AbstractDungeon.player == null) return;

        String selectType = ir.has("select_type") ? ir.get("select_type").getAsString() : "card_select";
        String prompt = ir.has("prompt") ? ir.get("prompt").getAsString() : "Select a card";
        int minSelect = ir.has("min_select") ? ir.get("min_select").getAsInt() : 1;
        boolean anyNumber = ir.has("max_select") ? ir.get("max_select").getAsInt() > 1 : false;

        if (!"card_select".equals(selectType)) return;

        JsonArray optArr = ir.has("options") ? ir.getAsJsonArray("options") : null;
        if (optArr == null || optArr.size() == 0) return;

        com.megacrit.cardcrawl.cards.CardGroup pool = new com.megacrit.cardcrawl.cards.CardGroup(
            com.megacrit.cardcrawl.cards.CardGroup.CardGroupType.UNSPECIFIED);
        for (int i = 0; i < optArr.size(); i++) {
            String cid = optArr.get(i).getAsString();
            pool.group.add(new crossspire.combat.CardStub(cid, 1,
                com.megacrit.cardcrawl.cards.AbstractCard.CardType.ATTACK,
                com.megacrit.cardcrawl.cards.AbstractCard.CardRarity.BASIC,
                com.megacrit.cardcrawl.cards.AbstractCard.CardTarget.ENEMY));
        }

        AbstractDungeon.gridSelectScreen.open(pool, minSelect, prompt, anyNumber);
        BaseMod.logger.info("MessageRouter opened remote GridCardSelectScreen: cards="
            + optArr.size() + " select=" + minSelect);
    }

    private void handleInteractResponse(String rawMessage) {
        JsonObject ir = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        BaseMod.logger.info("MessageRouter interact_response received");

        if (CrossSpireMod.isRoomHost()) {
            if (CrossSpireMod.stageHost != null && CrossSpireMod.stageHost.isStageHost()) {
                applyInteractResponse(ir);
            } else if (CrossSpireMod.stageHost != null) {
                String stageHostId = CrossSpireMod.stageHost.getStageHostId();
                if (stageHostId != null) {
                    CrossSpireMod.connectionManager.send(stageHostId, rawMessage);
                    BaseMod.logger.info("MessageRouter forwarded interact_response to stage host");
                }
            }
        } else if (CrossSpireMod.stageHost.isStageHost()) {
            applyInteractResponse(ir);
        }
    }

    private void applyInteractResponse(JsonObject ir) {
        if (AbstractDungeon.gridSelectScreen == null) return;
        com.megacrit.cardcrawl.screens.select.GridCardSelectScreen gcs = AbstractDungeon.gridSelectScreen;
        if (gcs.selectedCards == null) return;
        JsonArray sel = ir.has("selected") ? ir.getAsJsonArray("selected") : new JsonArray();
        for (int i = 0; i < sel.size(); i++) {
            String cid = sel.get(i).getAsString();
            com.megacrit.cardcrawl.cards.AbstractCard stub = new crossspire.combat.CardStub(cid, 1,
                com.megacrit.cardcrawl.cards.AbstractCard.CardType.ATTACK,
                com.megacrit.cardcrawl.cards.AbstractCard.CardRarity.BASIC,
                com.megacrit.cardcrawl.cards.AbstractCard.CardTarget.ENEMY);
            gcs.selectedCards.add(stub);
        }
        if (gcs.confirmButton != null) {
            gcs.confirmButton.hb.clicked = true;
        }
        BaseMod.logger.info("MessageRouter injected " + sel.size() + " cards + confirmed");
    }
}
