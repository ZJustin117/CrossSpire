package crossspire.sync;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;
import crossspire.combat.CombatResultApplyPolicy;
import crossspire.combat.CombatResultReplayer;
import crossspire.combat.CentralQueueManager;
import crossspire.combat.EventCapture;
import crossspire.combat.EventSyncPatches;
import crossspire.combat.MonsterTurnResultGate;
import crossspire.event.EventApprovalCoordinator;
import crossspire.event.EventChoiceSender;
import crossspire.event.EventOpenModeRegistry;
import crossspire.event.EventPlayerResultApplyPlanner;
import crossspire.event.EventRoomInstance;
import crossspire.event.EventRoomInstanceRegistry;
import crossspire.event.EventRoomOutcomePlanner;
import crossspire.event.FallbackNihResultPlanner;
import crossspire.event.NativeEventApprovalPatches;
import crossspire.map.MapDefinition;
import crossspire.map.MapHostVoteSender;
import crossspire.map.MapNavigation;
import crossspire.map.MapProtocolMapper;
import crossspire.map.MapRegisterSender;
import crossspire.map.MapHostRegistrationCoordinator;
import crossspire.map.StsMapDefinitionApplier;
import crossspire.map.NodeEntryCoordinator;
import crossspire.map.NodeInstance;
import crossspire.map.NodeInstanceAllocateSender;
import crossspire.map.NodeInstanceHostVoteSender;
import crossspire.map.NodeInstanceOpenedSender;
import crossspire.map.NodeGenerationPlanner;
import crossspire.map.PartyRoomPinTracker;
import crossspire.party.ActiveNodeTracker;
import crossspire.party.PartySnapshotCodec;
import crossspire.party.PartySnapshotSender;
import crossspire.party.PartyCoordinator;
import crossspire.party.PartyManager;
import crossspire.party.PartyState;
import crossspire.party.RoomNavigationGate;
import crossspire.network.HeartbeatManager;
import crossspire.network.PacketOperation;
import crossspire.network.Protocol;
import crossspire.network.RoomPinSender;
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
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import java.util.ArrayList;
import java.util.List;

public class MessageRouter {

    private final SyncExecutor syncExecutor;
    private final CentralQueueManager centralQueue;
    private final CombatResultReplayer resultReplayer;
    private final MonsterTurnResultGate monsterTurnResultGate = new MonsterTurnResultGate();
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
            if (!relayPartyCoordinatorMessage(msg, false)) return;
            if (!admitMonsterTurnResult(msg)) return;
            String partyId = msg.has("party_id") ? msg.get("party_id").getAsString()
                : PartyManager.DEFAULT_PARTY_ID;
            if (isLocalParty(partyId)) resultReplayer.handleCombatResult(rawMessage);
            // P6: if room host (not same as stage) receives stage-host monster_turn result, advance phase.
            maybeAdvanceAfterMonsterCombatResult(msg);
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
        } else if ("party_run_start_request".equals(type)) {
            CrossSpireMod.lobbyState.onPartyRunStartRequest(rawMessage);
        } else if ("party_run_start".equals(type)) {
            if (CrossSpireMod.isRoomHost()) {
                // Host already applied when broadcasting; still allow idempotent re-apply for self-loop skips.
                Protocol.PartyRunStart run =
                    Protocol.GSON.fromJson(rawMessage, Protocol.PartyRunStart.class);
                if (run != null && run.source != null && run.source.equals(CrossSpireMod.playerId)
                    && CrossSpireMod.lobbyState.isStarted()) {
                    BaseMod.logger.info("MessageRouter skip self party_run_start (already applied)");
                } else {
                    CrossSpireMod.lobbyState.applyPartyRunStart(rawMessage);
                }
            } else {
                CrossSpireMod.lobbyState.applyPartyRunStart(rawMessage);
            }
        } else if ("reward_phase_enter".equals(type)) {
            handleRewardPhaseEnter(rawMessage);
        } else if ("reward_done".equals(type)) {
            handleRewardDone(rawMessage);
        } else if ("reward_phase_complete".equals(type)) {
            handleRewardPhaseComplete(rawMessage);
        } else if ("room_exit_unlocked".equals(type)) {
            handleRoomExitUnlocked(rawMessage);
        } else if ("room_exit_locked".equals(type)) {
            handleRoomExitLocked(rawMessage);
        } else if ("reward_offer".equals(type) || "reward_pick".equals(type)
            || "reward_player_result".equals(type)) {
            BaseMod.logger.info("MessageRouter reward msg type=" + type
                + " (personal economy; gold not shared)");
        } else if ("invoke".equals(type)) {
            handleInvoke(rawMessage);
        } else if ("invoke_result".equals(type)) {
            BaseMod.logger.info("MessageRouter invoke_result received: " + rawMessage.substring(0, Math.min(120, rawMessage.length())));
            Protocol.InvokeResultMessage result = Protocol.GSON.fromJson(rawMessage, Protocol.InvokeResultMessage.class);
            if (CrossSpireMod.isRoomHost()) {
                String partyId = partyId(result.partyId);
                if (!PartyCoordinator.isMember(CrossSpireMod.partyManager, partyId, result.source)) return;
                String leaderId = PartyCoordinator.leaderId(CrossSpireMod.partyManager, partyId);
                if (!leaderId.equals(CrossSpireMod.playerId)) {
                    result.target = leaderId;
                    CrossSpireMod.sendToPlayer(leaderId, Protocol.GSON.toJson(result));
                    return;
                }
            }
            if (!PartyCoordinator.isLeader(CrossSpireMod.partyManager, partyId(result.partyId), CrossSpireMod.playerId)) return;
            RemoteReference.onInvokeResult(result);
            CrossSpireMod.centralQueueManager.onInvokeResult(result);
            broadcastCombatResult(result);
        } else if ("monster_intent".equals(type)) {
            syncExecutor.handleSync("monster_intent", null, 1, rawMessage);
        } else if ("event_result".equals(type)) {
            syncExecutor.handleEventResult(rawMessage);
            RemoteEventDisplay.hide();
        } else if ("event_transcript".equals(type)) {
            handleEventTranscript(rawMessage);
        } else if ("event_vote".equals(type)) {
            handleEventVote(rawMessage);
        } else if ("event_interface".equals(type)) {
            JsonObject ei = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
            String name = ei.has("event_id") ? ei.get("event_id").getAsString() : "?";
            String eventClass = ei.has("event_class") ? ei.get("event_class").getAsString() : "";
            int opts = ei.has("options") ? ei.getAsJsonArray("options").size() : 0;
            BaseMod.logger.info("MessageRouter event_interface: " + name + " options=" + opts);
            crossspire.ui.CrossSpireCommand.setLastEventClass(eventClass);
            if (!CrossSpireMod.stageHost.isStageHost()) {
                boolean localized = false;
                if (!eventClass.isEmpty()) {
                    try {
                        Class<?> cls = Class.forName(eventClass);
                        java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor();
                        ctor.setAccessible(true);
                        AbstractEvent ev = (AbstractEvent) ctor.newInstance();
                        ev.onEnterRoom();
                        localized = true;
                        BaseMod.logger.info("MessageRouter localized event: " + name);
                    } catch (Exception e) {
                        BaseMod.logger.info("MessageRouter cannot localize: " + name);
                    }
                }
                if (!localized) {
                String desc = ei.has("description") ? ei.get("description").getAsString() : "";
                JsonArray options = ei.has("options") ? ei.getAsJsonArray("options") : new JsonArray();
                RemoteEventDisplay.show(name, desc, options);
                }
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
            handlePlayerEndTurn(msg);
        } else if ("combat_phase".equals(type)) {
            if (!relayPartyCoordinatorMessage(msg, true)) return;
            handleCombatPhase(msg);
        } else if ("queue_submit".equals(type)) {
            handleQueueSubmit(rawMessage);
            QueueDisplay.resetEndTurn();
        } else if ("queue_update".equals(type)) {
            if (!relayPartyCoordinatorMessage(msg, true)) return;
            Protocol.QueueUpdateMessage upd = Protocol.GSON.fromJson(rawMessage, Protocol.QueueUpdateMessage.class);
            if (isLocalParty(upd.partyId)) QueueDisplay.onUpdate(upd.entries);
        } else if ("queue_empty".equals(type)) {
            if (!relayPartyCoordinatorMessage(msg, true)) return;
            // Legacy signal; combat_phase=queue_empty is authoritative when present.
            String partyId = msg.has("party_id") ? msg.get("party_id").getAsString()
                : PartyManager.DEFAULT_PARTY_ID;
            if (isLocalParty(partyId)) QueueDisplay.onQueueEmpty();
            crossspire.combat.CombatPhaseCoordinator.applyLocal(
                partyId, crossspire.combat.CombatPhase.QUEUE_EMPTY, "");
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
        } else if ("room_pins".equals(type)) {
            JsonObject pins = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
            BaseMod.logger.info("MessageRouter room_pins party="
                + partyId(pins.has("party_id") ? pins.get("party_id").getAsString() : null));
        } else if ("room_consensus".equals(type)) {
            handleRoomConsensus(rawMessage);
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
        result.partyId = inv.partyId;
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
            list.add(crossspire.combat.ApplyPowerEffects.applyPowerEffect(
                "Vulnerable", targetId, card.magicNumber, CrossSpireMod.playerId));
        }
        return list.toArray(new Protocol.EffectDescription[0]);
    }

    private void handleCombatPhase(JsonObject msg) {
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        String partyId = msg.has("party_id") ? msg.get("party_id").getAsString()
            : PartyManager.DEFAULT_PARTY_ID;
        String phase = msg.has("phase") ? msg.get("phase").getAsString() : "";
        String tx = msg.has("transaction_id") ? msg.get("transaction_id").getAsString() : "";
        BaseMod.logger.info("MessageRouter combat_phase party=" + partyId + ": " + phase + " tx=" + tx);
        if (!crossspire.combat.CombatPhaseCoordinator.tryApplyRemote(
                partyId, source, PartyCoordinator.leaderId(CrossSpireMod.partyManager, partyId), phase, tx)) {
            return;
        }
        if (crossspire.combat.CombatPhase.QUEUE_EMPTY.equals(phase)
            || crossspire.combat.CombatPhase.PLAYER_TURN.equals(phase)) {
            Gdx.app.postRunnable(new Runnable() {
                @Override public void run() {
                    if (AbstractDungeon.overlayMenu != null
                        && AbstractDungeon.overlayMenu.endTurnButton != null) {
                        AbstractDungeon.overlayMenu.endTurnButton.enable();
                    }
                }
            });
        }
    }

    /** The party leader advances after the authorized NodeInstanceHost monster result. */
    private void maybeAdvanceAfterMonsterCombatResult(JsonObject msg) {
        String partyId = msg.has("party_id") ? msg.get("party_id").getAsString()
            : PartyManager.DEFAULT_PARTY_ID;
        if (!PartyCoordinator.isLeader(CrossSpireMod.partyManager, partyId, CrossSpireMod.playerId)) return;
        if (!crossspire.combat.CombatPhase.MONSTER_TURN.equals(
            crossspire.combat.CombatPhaseCoordinator.getCurrentPhase(partyId))) {
            return;
        }
        String monsterId = msg.has("monsterId") ? msg.get("monsterId").getAsString()
            : (msg.has("monster_id") ? msg.get("monster_id").getAsString() : "");
        if (!"monster_turn".equals(monsterId)) {
            return;
        }
        crossspire.combat.CombatPhaseCoordinator.broadcast(
            partyId, crossspire.combat.CombatPhase.POST_MONSTER_TURN);
        crossspire.combat.CombatPhaseCoordinator.broadcast(
            partyId, crossspire.combat.CombatPhase.PLAYER_TURN);
    }

    private boolean admitMonsterTurnResult(JsonObject msg) {
        String monsterId = msg.has("monsterId") ? msg.get("monsterId").getAsString()
            : (msg.has("monster_id") ? msg.get("monster_id").getAsString() : "");
        if (!"monster_turn".equals(monsterId)) return true;
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        int seq = msg.has("seq") ? msg.get("seq").getAsInt() : -1;
        String tx = msg.has("turn_transaction_id")
            ? msg.get("turn_transaction_id").getAsString() : "";
        String stageHostId = CrossSpireMod.stageHost != null
            ? CrossSpireMod.stageHost.getStageHostId() : "";
        String partyId = msg.has("party_id") ? msg.get("party_id").getAsString()
            : PartyManager.DEFAULT_PARTY_ID;
        return monsterTurnResultGate.admit(
            crossspire.combat.CombatPhaseCoordinator.getCurrentPhase(partyId),
            crossspire.combat.CombatPhaseCoordinator.getLastTransactionId(partyId),
            monsterId, source, stageHostId, seq, tx);
    }

    private void handlePlayerEndTurn(JsonObject msg) {
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        String partyId = msg.has("party_id") ? msg.get("party_id").getAsString()
            : PartyManager.DEFAULT_PARTY_ID;
        BaseMod.logger.info("MessageRouter player_end_turn from "
            + (source.length() >= 8 ? source.substring(0, 8) : source));

        if (CrossSpireMod.isRoomHost()) {
            if (!PartyCoordinator.isMember(CrossSpireMod.partyManager, partyId, source)) {
                BaseMod.logger.info("MessageRouter reject end_turn outside party=" + partyId);
                return;
            }
            String leaderId = PartyCoordinator.leaderId(CrossSpireMod.partyManager, partyId);
            if (!leaderId.equals(CrossSpireMod.playerId)) {
                CrossSpireMod.sendToPlayer(leaderId, Protocol.GSON.toJson(msg));
                return;
            }
        }

        if (PartyCoordinator.isLeader(CrossSpireMod.partyManager, partyId, CrossSpireMod.playerId)
            && CrossSpireMod.partyEndTurnTracker != null && !source.isEmpty()) {
            PartyState party = CrossSpireMod.partyManager.getParty(partyId);
            boolean consensus = CrossSpireMod.partyEndTurnTracker.markReady(party, source);
            BaseMod.logger.info("MessageRouter end_turn party=" + partyId + " ready="
                + CrossSpireMod.partyEndTurnTracker.readyCount(partyId) + "/" + party.memberIds.size());
            if (consensus) {
                CrossSpireMod.partyEndTurnTracker.clear(partyId);
                // P6: pre_monster → monster_turn (host-driven; stage host runs AI in MONSTER_TURN)
                crossspire.combat.CombatPhaseCoordinator.broadcast(
                    partyId, crossspire.combat.CombatPhase.PRE_MONSTER_TURN);
                crossspire.combat.CombatPhaseCoordinator.broadcast(
                    partyId, crossspire.combat.CombatPhase.MONSTER_TURN);
            }
        }

        // Mirror only a teammate's end-turn; another party must not affect local UI.
        if (source.equals(CrossSpireMod.playerId)) return;
        if (!isLocalParty(partyId)) return;
        if (AbstractDungeon.player == null || AbstractDungeon.overlayMenu == null) return;
        com.megacrit.cardcrawl.ui.buttons.EndTurnButton btn = AbstractDungeon.overlayMenu.endTurnButton;
        if (btn != null && btn.enabled) {
            BaseMod.logger.info("MessageRouter player_end_turn: ending turn (remote mirror)");
            EndTurnSyncPatches.suppressEndTurn = true;
            AbstractDungeon.overlayMenu.endTurnButton.disable(true);
            EndTurnSyncPatches.suppressEndTurn = false;
        }
    }

    private void handleQueueSubmit(String rawMessage) {
        Protocol.QueueSubmitMessage pkt = Protocol.GSON.fromJson(rawMessage, Protocol.QueueSubmitMessage.class);
        pkt.partyId = pkt.partyId == null || pkt.partyId.isEmpty()
            ? PartyManager.DEFAULT_PARTY_ID : pkt.partyId;
        if (!PartyCoordinator.isMember(CrossSpireMod.partyManager, pkt.partyId, pkt.source)) {
            BaseMod.logger.info("MessageRouter reject queue_submit outside party=" + pkt.partyId);
            return;
        }
        if (CrossSpireMod.isRoomHost()) {
            String leaderId = PartyCoordinator.leaderId(CrossSpireMod.partyManager, pkt.partyId);
            if (!leaderId.equals(CrossSpireMod.playerId)) {
                CrossSpireMod.sendToPlayer(leaderId, Protocol.GSON.toJson(pkt));
                return;
            }
        }
        if (!PartyCoordinator.isLeader(CrossSpireMod.partyManager, pkt.partyId, CrossSpireMod.playerId)) return;
        CrossSpireMod.centralQueueManager.onQueueSubmit(pkt);
    }

    private static boolean isLocalParty(String partyId) {
        String effectivePartyId = partyId == null || partyId.isEmpty()
            ? PartyManager.DEFAULT_PARTY_ID : partyId;
        if (CrossSpireMod.partyManager == null || CrossSpireMod.playerId == null
            || CrossSpireMod.playerId.isEmpty()) return PartyManager.DEFAULT_PARTY_ID.equals(effectivePartyId);
        String localPartyId = CrossSpireMod.partyManager.getPartyIdForPlayer(CrossSpireMod.playerId);
        return effectivePartyId.equals(localPartyId != null ? localPartyId : PartyManager.DEFAULT_PARTY_ID);
    }

    private void broadcastCombatResult(Protocol.InvokeResultMessage result) {
        Protocol.CombatResultMessage broadcast = new Protocol.CombatResultMessage();
        // Preserve original REAL executor; do not rewrite to room host.
        broadcast.executorId = result.source != null ? result.source : CrossSpireMod.playerId;
        broadcast.source = CrossSpireMod.playerId;
        broadcast.seq = CrossSpireMod.nextSeq();
        broadcast.partyId = result.partyId == null || result.partyId.isEmpty()
            ? PartyManager.DEFAULT_PARTY_ID : result.partyId;
        broadcast.effects = result.effects;
        broadcast.operationSequence = result.operationSequence;

        String raw = Protocol.GSON.toJson(broadcast);
        if (CrossSpireMod.isConnected()) {
            CrossSpireMod.sendToParty(broadcast.partyId, raw);
            BaseMod.logger.info("MessageRouter broadcast combat_result executor="
                + (broadcast.executorId.length() >= 8 ? broadcast.executorId.substring(0, 8) : broadcast.executorId)
                + " effects=" + (result.effects != null ? result.effects.length : 0));
        }
        // sendToParty never delivers to self; induce peer results locally when policy allows.
        String executor = broadcast.executorId != null ? broadcast.executorId : "";
        if (CombatResultApplyPolicy.shouldLocalInduceAfterBroadcast(CrossSpireMod.playerId, executor)
            && isLocalParty(broadcast.partyId)) {
            scheduleCombatResultReplay(raw);
        }
    }

    private void scheduleCombatResultReplay(final String rawMessage) {
        if (rawMessage == null || resultReplayer == null) return;
        try {
            Gdx.app.postRunnable(new Runnable() {
                @Override public void run() {
                    try {
                        resultReplayer.handleCombatResult(rawMessage);
                    } catch (Exception e) {
                        BaseMod.logger.error("MessageRouter combat_result replay: "
                            + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                    }
                }
            });
        } catch (Exception e) {
            resultReplayer.handleCombatResult(rawMessage);
        }
    }

    /** RoomHost validates and relays party coordinator output without owning gameplay state. */
    private boolean relayPartyCoordinatorMessage(JsonObject msg, boolean requireLeader) {
        if (!CrossSpireMod.isRoomHost()) return true;
        String partyId = partyId(msg.has("party_id") ? msg.get("party_id").getAsString() : null);
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        if (!PartyCoordinator.isMember(CrossSpireMod.partyManager, partyId, source)) {
            BaseMod.logger.info("MessageRouter reject party output outside party=" + partyId);
            return false;
        }
        if (requireLeader && !PartyCoordinator.isLeader(CrossSpireMod.partyManager, partyId, source)) {
            BaseMod.logger.info("MessageRouter reject non-leader party output=" + partyId);
            return false;
        }
        if (!source.equals(CrossSpireMod.playerId)) {
            CrossSpireMod.sendToParty(partyId, Protocol.GSON.toJson(msg));
        }
        return true;
    }

    private static String partyId(String partyId) {
        return partyId == null || partyId.isEmpty() ? PartyManager.DEFAULT_PARTY_ID : partyId;
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
        String partyId = partyId(msg.has("party_id") ? msg.get("party_id").getAsString() : null);
        int roomIndex = msg.has("room") ? msg.get("room").getAsInt() : -1;
        String preResolvedNodeId = msg.has("node_id") ? msg.get("node_id").getAsString() : null;
        if (source.isEmpty()) return;
        if (CrossSpireMod.partyManager == null) return;

        PartyState party = CrossSpireMod.partyManager.getParty(partyId);
        if (party == null) return;

        // Remote party leader receives host-forwarded pins with node_id already resolved.
        if (!CrossSpireMod.isRoomHost()
            && PartyCoordinator.isLeader(CrossSpireMod.partyManager, partyId, CrossSpireMod.playerId)
            && preResolvedNodeId != null && !preResolvedNodeId.isEmpty()) {
            applyLeaderRoomPin(party, source, preResolvedNodeId);
            return;
        }

        if (!CrossSpireMod.isRoomHost() || CrossSpireMod.roomHost == null) {
            BaseMod.logger.info("MessageRouter room_pin from "
                + (source.length() >= 8 ? source.substring(0, 8) : source)
                + " room=" + roomIndex);
            return;
        }
        if (roomIndex < 0 || !party.memberIds.contains(source)
            || party.mapInstanceId == null || party.mapInstanceId.isEmpty()) {
            BaseMod.logger.info("MessageRouter reject room_pin unbound party=" + partyId
                + " map=" + (party.mapInstanceId != null ? party.mapInstanceId : "")
                + " pos=" + party.mapPosition);
            return;
        }
        if (CrossSpireMod.roomNavigationGate != null
            && CrossSpireMod.roomNavigationGate.blocksRoomPin()
            && (CrossSpireMod.roomNavigationGate.getPartyId().isEmpty()
                || partyId.equals(CrossSpireMod.roomNavigationGate.getPartyId()))) {
            BaseMod.logger.info("MessageRouter reject room_pin exit_locked party=" + partyId
                + " " + CrossSpireMod.roomNavigationGate.summary());
            return;
        }
        if (CrossSpireMod.rewardPhaseTracker != null
            && CrossSpireMod.rewardPhaseTracker.blocksRoomPin()
            && partyId.equals(CrossSpireMod.rewardPhaseTracker.getPartyId())) {
            BaseMod.logger.info("MessageRouter reject room_pin reward_phase_active party=" + partyId);
            return;
        }
        if (ActiveNodeTracker.isRewardPhaseActive()
            && partyId.equals(ActiveNodeTracker.getPartyId())) {
            BaseMod.logger.info("MessageRouter reject room_pin local reward_phase party=" + partyId);
            return;
        }
        MapDefinition map = CrossSpireMod.roomHost.getMapRegistry().get(party.mapInstanceId);
        String nodeId = MapNavigation.resolveOutgoing(map, party.mapPosition, roomIndex);
        if (nodeId == null) {
            BaseMod.logger.info("MessageRouter reject room_pin bad index party=" + partyId
                + " from=" + party.mapPosition + " idx=" + roomIndex
                + " map=" + party.mapInstanceId
                + " outgoing=" + (map != null && map.getNode(party.mapPosition) != null
                    ? map.getNode(party.mapPosition).outgoingNodeIds : "null"));
            return;
        }

        // Party leader aggregates pins; RoomHost only routes when not the leader.
        if (!PartyCoordinator.isLeader(CrossSpireMod.partyManager, partyId, CrossSpireMod.playerId)) {
            String leaderId = PartyCoordinator.leaderId(CrossSpireMod.partyManager, partyId);
            if (leaderId == null || leaderId.isEmpty()) return;
            msg.addProperty("node_id", nodeId);
            msg.addProperty("party_id", partyId);
            CrossSpireMod.sendToPlayer(leaderId, Protocol.GSON.toJson(msg));
            return;
        }

        applyLeaderRoomPin(party, source, nodeId);
    }

    /**
     * Leader-local pin application.
     * When the leader is also RoomHost, pin state lives on RoomHost.
     * When the leader is a client, pin state is held in a local tracker until consensus
     * is sent back to RoomHost for allocation (RoomHost is source of truth for NodeInstance).
     */
    private static final PartyRoomPinTracker localLeaderPinTracker = new PartyRoomPinTracker();

    private void applyLeaderRoomPin(PartyState party, String source, String nodeId) {
        if (party == null || CrossSpireMod.partyManager == null) return;
        party = CrossSpireMod.partyManager.getParty(party.partyId);
        if (party == null) return;
        PartyRoomPinTracker tracker = CrossSpireMod.isRoomHost() && CrossSpireMod.roomHost != null
            ? CrossSpireMod.roomHost.getPartyRoomPinTracker() : localLeaderPinTracker;
        if (!tracker.pin(party, source, nodeId)) {
            BaseMod.logger.info("MessageRouter reject room_pin member party=" + party.partyId);
            return;
        }
        String pinsMsg = RoomPinSender.buildRoomPins(
            CrossSpireMod.playerId, party.partyId, tracker.pins(party.partyId));
        if (CrossSpireMod.isRoomHost()) {
            CrossSpireMod.sendToParty(party.partyId, pinsMsg);
        } else {
            CrossSpireMod.send(pinsMsg);
        }
        String consensus = tracker.consensusNodeId(party);
        if (consensus == null) return;
        // Clear leader-local pins immediately so a concurrent second pin cannot
        // re-fire consensus for the same visit with a stale member set.
        tracker.clear(party.partyId);
        String consensusMsg = RoomPinSender.buildRoomConsensus(
            CrossSpireMod.playerId, party.partyId, party.mapInstanceId, consensus);
        if (CrossSpireMod.isRoomHost()) {
            handleRoomConsensus(consensusMsg);
        } else {
            CrossSpireMod.send(consensusMsg);
        }
        BaseMod.logger.info("MessageRouter room_pin consensus party=" + party.partyId
            + " node=" + consensus);
    }

    private void handleRoomConsensus(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        String partyId = partyId(msg.has("party_id") ? msg.get("party_id").getAsString() : null);
        String nodeId = msg.has("node_id") ? msg.get("node_id").getAsString() : "";
        String mapInstanceId = msg.has("map_instance_id") ? msg.get("map_instance_id").getAsString() : "";

        // Legacy integer room_consensus fallback for old clients.
        if ((nodeId == null || nodeId.isEmpty()) && msg.has("room")) {
            int room = msg.get("room").getAsInt();
            BaseMod.logger.info("MessageRouter legacy room_consensus room=" + room);
            SyncExecutor.executeRoomConsensus(room);
            return;
        }

        if (!CrossSpireMod.isRoomHost() || CrossSpireMod.roomHost == null
            || CrossSpireMod.partyManager == null) {
            BaseMod.logger.info("MessageRouter room_consensus party=" + partyId + " node=" + nodeId);
            return;
        }
        PartyState party = CrossSpireMod.partyManager.getParty(partyId);
        if (party == null || !PartyCoordinator.isLeader(CrossSpireMod.partyManager, partyId, source)
            || !mapInstanceId.equals(party.mapInstanceId)) {
            BaseMod.logger.info("MessageRouter reject room_consensus party=" + partyId);
            return;
        }
        NodeInstance allocated = CrossSpireMod.roomHost.getNodeEntryCoordinator()
            .allocateOnConsensus(party, nodeId, 1);
        if (allocated == null) {
            BaseMod.logger.info("MessageRouter reject allocate party=" + partyId + " node=" + nodeId);
            return;
        }
        PartyState entered = CrossSpireMod.partyManager.enterNode(
            partyId, allocated.nodeId, allocated.nodeInstanceId);
        if (entered != null) {
            CrossSpireMod.send(PartySnapshotSender.build());
        }
        CrossSpireMod.roomHost.getPartyRoomPinTracker().clear(partyId);
        StandardPacket allocate = NodeInstanceAllocateSender.build(
            allocated, "visit-" + allocated.visitId);
        String allocateJson = StandardPacket.toJson(allocate);
        // RoomHost has no loopback socket when NIH is also the host.
        if (allocated.nodeInstanceHostId != null
            && allocated.nodeInstanceHostId.equals(CrossSpireMod.playerId)) {
            handleNodeInstanceAllocate(allocate);
        } else {
            CrossSpireMod.sendToPlayer(allocated.nodeInstanceHostId, allocateJson);
        }
        BaseMod.logger.info("MessageRouter node_instance_allocate party=" + partyId
            + " node=" + allocated.nodeId + " nih=" + allocated.nodeInstanceHostId
            + " id=" + allocated.nodeInstanceId);
    }

    private void handleNodeInstanceAllocate(StandardPacket pkt) {
        Protocol.NodeInstanceAllocatePayload payload = Protocol.GSON.fromJson(
            pkt.payload, Protocol.NodeInstanceAllocatePayload.class);
        if (payload == null || payload.nodeInstance == null) return;
        Protocol.NodeInstanceInfo info = payload.nodeInstance;
        BaseMod.logger.info("MessageRouter node_instance_allocate party=" + info.partyId
            + " node=" + info.nodeId + " id=" + info.nodeInstanceId
            + " nih=" + info.nodeInstanceHostId);
        if (CrossSpireMod.playerId == null || !CrossSpireMod.playerId.equals(info.nodeInstanceHostId)) {
            BaseMod.logger.info("MessageRouter skip allocate not local NIH");
            return;
        }
        Protocol.NodeGenerationResult generation = NodeGenerationPlanner.plan(
            new crossspire.map.MapNode(info.nodeId, info.roomType, java.util.Collections.<String>emptyList()), info);
        if (generation == null) {
            BaseMod.logger.info("MessageRouter reject allocate unknown node generation=" + info.nodeId);
            return;
        }
        info.status = "generating";
        info.generationRevision = 1;
        StandardPacket commit = NodeInstanceOpenedSender.buildCommit(info, generation, 1);
        String commitJson = StandardPacket.toJson(commit);
        if (CrossSpireMod.isRoomHost()) {
            handleNodeGenerationCommit(commit);
        } else {
            CrossSpireMod.send(commitJson);
        }
        BaseMod.logger.info("MessageRouter node_generation_commit party=" + info.partyId
            + " node=" + info.nodeId + " type=" + generation.roomType);
    }

    private void handleNodeGenerationCommit(StandardPacket pkt) {
        if (!CrossSpireMod.isRoomHost() || CrossSpireMod.roomHost == null
            || CrossSpireMod.partyManager == null) {
            return;
        }
        Protocol.NodeGenerationCommitPayload commit = Protocol.GSON.fromJson(
            pkt.payload, Protocol.NodeGenerationCommitPayload.class);
        if (commit == null) return;
        String partyId = partyId(commit.partyId);
        PartyState party = CrossSpireMod.partyManager.getParty(partyId);
        NodeInstance allocatedNode = CrossSpireMod.roomHost.getNodeInstanceRegistry()
            .getByInstanceId(commit.nodeInstanceId);
        if (allocatedNode == null) return;
        Protocol.NodeInstanceInfo allocated = NodeInstanceAllocateSender.info(allocatedNode);
        if (!CrossSpireMod.roomHost.getNodeOpenCoordinator()
            .acceptCommit(party, pkt.source, allocated, commit)) {
            BaseMod.logger.info("MessageRouter reject node_generation_commit party=" + partyId);
            return;
        }
        allocated.status = "active";
        allocated.generationRevision = commit.generationRevision;
        StandardPacket opened = NodeInstanceOpenedSender.buildOpened(
            allocated, commit.generationResult);
        CrossSpireMod.sendToParty(partyId, StandardPacket.toJson(opened));
        if (pkt.source.equals(CrossSpireMod.playerId)) {
            SyncExecutor.openNodeInstanceAsHost(allocated, commit.generationResult);
            publishEventInterface(allocated, commit.generationResult);
        } else {
            SyncExecutor.openNodeInstanceAsMember(allocated, commit.generationResult);
        }
        BaseMod.logger.info("MessageRouter node_instance_opened party=" + partyId
            + " node=" + allocated.nodeId + " id=" + allocated.nodeInstanceId
            + " type=" + (commit.generationResult != null
                ? commit.generationResult.roomType : "?"));
    }

    private void handleNodeInstanceOpened(StandardPacket pkt) {
        if (CrossSpireMod.isRoomHost()) return;
        Protocol.NodeInstanceOpenedPayload opened = Protocol.GSON.fromJson(
            pkt.payload, Protocol.NodeInstanceOpenedPayload.class);
        if (opened == null || opened.nodeInstance == null) return;
        Protocol.NodeInstanceInfo info = opened.nodeInstance;
        BaseMod.logger.info("MessageRouter node_instance_opened party=" + info.partyId
            + " node=" + info.nodeId + " id=" + info.nodeInstanceId);
        if (CrossSpireMod.playerId != null
            && CrossSpireMod.playerId.equals(info.nodeInstanceHostId)) {
            SyncExecutor.openNodeInstanceAsHost(info, opened.generationResult);
            publishEventInterface(info, opened.generationResult);
            return;
        }
        SyncExecutor.openNodeInstanceAsMember(info, opened.generationResult);
    }

    private void publishEventInterface(Protocol.NodeInstanceInfo info,
                                       Protocol.NodeGenerationResult generation) {
        if (generation == null || !"event".equals(generation.roomType)
            || generation.eventInterface == null) {
            return;
        }
        StandardPacket iface = EventChoiceSender.interfacePacket(generation.eventInterface);
        if (CrossSpireMod.isRoomHost()) {
            handleEventInterfacePacket(iface);
        } else {
            CrossSpireMod.send(StandardPacket.toJson(iface));
        }
        BaseMod.logger.info("MessageRouter event_interface publish party=" + info.partyId
            + " event=" + generation.eventInterface.eventInstanceId);
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
        } else if (PacketOperation.PARTY_SNAPSHOT.equals(op)) {
            if (CrossSpireMod.isRoomHost() || !pkt.source.equals(CrossSpireMod.hostId)) {
                return;
            }
            Protocol.PartySnapshotPayload partySnapshot = Protocol.GSON.fromJson(
                payload, Protocol.PartySnapshotPayload.class);
            if (!PartySnapshotCodec.apply(CrossSpireMod.partyManager, partySnapshot)) {
                BaseMod.logger.info("MessageRouter rejected invalid party_snapshot");
            } else {
                BaseMod.logger.info("MessageRouter applied party_snapshot parties="
                    + CrossSpireMod.partyManager.snapshot().size());
            }
        } else if (PacketOperation.MAP_HOST_VOTE.equals(op)) {
            handleMapHostVote(pkt);
        } else if (PacketOperation.MAP_HOST_RESULT.equals(op)) {
            handleMapHostResult(pkt);
        } else if (PacketOperation.MAP_REGISTER.equals(op)) {
            handleMapRegister(pkt);
        } else if (PacketOperation.MAP_REGISTERED.equals(op)) {
            handleMapRegistered(pkt);
        } else if (PacketOperation.NODE_INSTANCE_HOST_VOTE.equals(op)) {
            handleNodeInstanceHostVote(pkt);
        } else if (PacketOperation.NODE_INSTANCE_HOST_RESULT.equals(op)) {
            handleNodeInstanceHostResult(pkt);
        } else if (PacketOperation.NODE_INSTANCE_ALLOCATE.equals(op)) {
            handleNodeInstanceAllocate(pkt);
        } else if (PacketOperation.NODE_GENERATION_COMMIT.equals(op)) {
            handleNodeGenerationCommit(pkt);
        } else if (PacketOperation.NODE_INSTANCE_OPENED.equals(op)) {
            handleNodeInstanceOpened(pkt);
        } else if (PacketOperation.EVENT_INTERFACE.equals(op)) {
            handleEventInterfacePacket(pkt);
        } else if (PacketOperation.EVENT_CHOICE_REQUEST.equals(op)) {
            handleEventChoiceRequest(pkt);
        } else if (PacketOperation.EVENT_CHOICE_APPROVED.equals(op)) {
            handleEventChoiceApproved(pkt);
        } else if (PacketOperation.EVENT_CHOICE_REJECTED.equals(op)) {
            handleEventChoiceRejected(pkt);
        } else if (PacketOperation.EVENT_PLAYER_RESULT.equals(op)) {
            handleEventPlayerResult(pkt);
        } else {
            BaseMod.logger.info("MessageRouter stdpkt: " + op + " (not yet routed)");
        }
    }

    private void handleEventInterfacePacket(StandardPacket pkt) {
        Protocol.EventInterfacePayload iface = Protocol.GSON.fromJson(
            pkt.payload, Protocol.EventInterfacePayload.class);
        if (iface == null) return;
        String partyId = partyId(iface.partyId);
        if (!CrossSpireMod.isRoomHost()) {
            openOrBindPartyEvent(iface);
            BaseMod.logger.info("MessageRouter event_interface party=" + partyId
                + " event=" + iface.eventInstanceId);
            return;
        }
        PartyState party = CrossSpireMod.partyManager != null
            ? CrossSpireMod.partyManager.getParty(partyId) : null;
        if (CrossSpireMod.roomHost == null || !CrossSpireMod.roomHost
            .getEventApprovalCoordinator().registerInterface(iface, party)) {
            BaseMod.logger.info("MessageRouter reject event_interface party=" + partyId);
            return;
        }
        CrossSpireMod.sendToParty(partyId, StandardPacket.toJson(pkt));
        openOrBindPartyEvent(iface);
        BaseMod.logger.info("MessageRouter event_interface registered party=" + partyId
            + " event=" + iface.eventInstanceId);
    }

    private static void openOrBindPartyEvent(Protocol.EventInterfacePayload iface) {
        if (iface == null) return;
        if (AbstractDungeon.getCurrRoom() != null
            && AbstractDungeon.getCurrRoom().event != null
            && NativeEventApprovalPatches.bindIfMatching(
                AbstractDungeon.getCurrRoom().event, iface)) {
            BaseMod.logger.info("MessageRouter bound native event approval event="
                + iface.eventInstanceId);
            return;
        }
        SyncExecutor.enterRemoteEvent(iface);
    }

    private void handleEventChoiceRequest(StandardPacket pkt) {
        if (!CrossSpireMod.isRoomHost() || CrossSpireMod.roomHost == null
            || CrossSpireMod.partyManager == null) return;
        Protocol.EventChoiceRequestPayload request = Protocol.GSON.fromJson(
            pkt.payload, Protocol.EventChoiceRequestPayload.class);
        if (request == null) return;
        String partyId = partyId(request.partyId);
        EventApprovalCoordinator.Decision decision = CrossSpireMod.roomHost
            .getEventApprovalCoordinator().decide(
                CrossSpireMod.partyManager.getParty(partyId), pkt.source, request);
        if (decision.approved) {
            deliverEventApprovals(partyId, decision);
            BaseMod.logger.info("MessageRouter event_choice_approved party=" + partyId
                + " request=" + request.requestId
                + " approvals=" + decision.approvals.size());
        } else {
            decision.payload.reason = decision.reason;
            StandardPacket reply = EventChoiceSender.rejectedPacket(decision.payload);
            if (pkt.source.equals(CrossSpireMod.playerId)) {
                handleEventChoiceRejected(reply);
            } else {
                CrossSpireMod.sendToPlayer(pkt.source, StandardPacket.toJson(reply));
            }
            BaseMod.logger.info("MessageRouter event_choice_rejected party=" + partyId
                + " request=" + request.requestId + " reason=" + decision.reason);
        }
    }

    private void deliverEventApprovals(String partyId, EventApprovalCoordinator.Decision decision) {
        PartyState party = CrossSpireMod.partyManager != null
            ? CrossSpireMod.partyManager.getParty(partyId) : null;
        for (Protocol.EventChoiceDecisionPayload payload : decision.approvals) {
            if (payload == null) continue;
            StandardPacket reply = EventChoiceSender.approvedPacket(payload);
            String chooser = CrossSpireMod.roomHost != null
                ? CrossSpireMod.roomHost.getEventApprovalCoordinator()
                    .chooserForRequest(payload.eventInstanceId, payload.requestId)
                : null;
            if (chooser == null || chooser.equals(CrossSpireMod.playerId)) {
                handleEventChoiceApproved(reply);
            } else {
                CrossSpireMod.sendToPlayer(chooser, StandardPacket.toJson(reply));
            }
            maybeEmitNihFallbackResult(partyId, chooser != null ? chooser : CrossSpireMod.playerId,
                payload);
        }
        if (party != null) {
            BaseMod.logger.info("MessageRouter voting/individual approvals delivered party="
                + partyId + " count=" + decision.approvals.size());
        }
    }

    private void handleEventChoiceApproved(StandardPacket pkt) {
        Protocol.EventChoiceDecisionPayload decision = Protocol.GSON.fromJson(
            pkt.payload, Protocol.EventChoiceDecisionPayload.class);
        if (decision != null) {
            NativeEventApprovalPatches.approved(decision);
            RemoteEventDisplay.onChoiceApproved(decision);
            BaseMod.logger.info("MessageRouter event_choice_approved event="
                + decision.eventInstanceId + " request=" + decision.requestId);
        }
    }

    /**
     * When local is the party's NodeInstanceHost and the event was opened as fallback UI,
     * produce a diagnostic personal result for the chooser. Native opens capture real deltas
     * on the chooser after buttonEffect instead.
     */
    private void maybeEmitNihFallbackResult(String partyId, String chooserId,
                                            Protocol.EventChoiceDecisionPayload decision) {
        if (decision == null || CrossSpireMod.partyManager == null) return;
        if (!EventOpenModeRegistry.isFallback(decision.eventInstanceId)) {
            BaseMod.logger.info("MessageRouter skip NIH diagnostic result for native event="
                + decision.eventInstanceId);
            return;
        }
        PartyState party = CrossSpireMod.partyManager.getParty(partyId);
        if (party == null || party.nodeInstanceHostId == null
            || !party.nodeInstanceHostId.equals(CrossSpireMod.playerId)) {
            return;
        }
        Protocol.EventInterfacePayload iface = CrossSpireMod.roomHost != null
            ? CrossSpireMod.roomHost.getEventApprovalCoordinator()
                .getInterface(decision.eventInstanceId)
            : null;
        Protocol.EventPlayerResultPayload result = FallbackNihResultPlanner.plan(iface, decision, chooserId);
        if (result == null) {
            BaseMod.logger.info("MessageRouter NIH fallback result skipped event="
                + decision.eventInstanceId + " request=" + decision.requestId);
            return;
        }
        StandardPacket resultPkt = EventChoiceSender.resultPacket(result);
        if (CrossSpireMod.isRoomHost()) {
            handleEventPlayerResult(resultPkt);
        } else {
            CrossSpireMod.send(StandardPacket.toJson(resultPkt));
        }
        BaseMod.logger.info("MessageRouter NIH fallback result event=" + result.eventInstanceId
            + " request=" + result.requestId + " chooser=" + chooserId
            + " effects=" + (result.effects != null ? result.effects.length : 0));
    }

    private void handleEventChoiceRejected(StandardPacket pkt) {
        Protocol.EventChoiceDecisionPayload decision = Protocol.GSON.fromJson(
            pkt.payload, Protocol.EventChoiceDecisionPayload.class);
        if (decision != null) {
            NativeEventApprovalPatches.rejected(decision);
            RemoteEventDisplay.onChoiceRejected(decision);
            BaseMod.logger.info("MessageRouter event_choice_rejected event="
                + decision.eventInstanceId + " request=" + decision.requestId
                + " reason=" + decision.reason);
        }
    }

    private void handleEventPlayerResult(StandardPacket pkt) {
        Protocol.EventPlayerResultPayload result = Protocol.GSON.fromJson(
            pkt.payload, Protocol.EventPlayerResultPayload.class);
        if (result == null) return;
        String partyId = partyId(result.partyId);
        if (!CrossSpireMod.isRoomHost()) {
            applyPersonalEventResult(result);
            if (result.sharedOutcome != null) {
                BaseMod.logger.info("MessageRouter shared_outcome type=" + result.sharedOutcome.type
                    + " instance=" + result.sharedOutcome.instanceId
                    + " option=" + result.sharedOutcome.optionIndex
                    + " encounter=" + result.sharedOutcome.encounter);
            }
            BaseMod.logger.info("MessageRouter event_player_result party=" + partyId
                + " request=" + result.requestId);
            return;
        }
        PartyState party = CrossSpireMod.partyManager != null
            ? CrossSpireMod.partyManager.getParty(partyId) : null;
        if (CrossSpireMod.roomHost == null || !CrossSpireMod.roomHost
            .getEventApprovalCoordinator().acceptPlayerResult(party, pkt.source, result)) {
            BaseMod.logger.info("MessageRouter reject event_player_result party=" + partyId
                + " request=" + result.requestId);
            return;
        }
        attachSharedOutcomeIfNeeded(party, result);
        // Re-serialize payload after optional shared_outcome enrichment.
        StandardPacket out = EventChoiceSender.resultPacket(result);
        out.source = pkt.source;
        CrossSpireMod.sendToParty(partyId, StandardPacket.toJson(out));
        applyPersonalEventResult(result);
        BaseMod.logger.info("MessageRouter event_player_result party=" + partyId
            + " request=" + result.requestId
            + (result.sharedOutcome != null
                ? (" shared=" + result.sharedOutcome.type + ":" + result.sharedOutcome.instanceId)
                : ""));
    }

    private void attachSharedOutcomeIfNeeded(PartyState party, Protocol.EventPlayerResultPayload result) {
        if (party == null || result == null || result.sharedOutcome != null
            || CrossSpireMod.roomHost == null) {
            return;
        }
        EventApprovalCoordinator coordinator = CrossSpireMod.roomHost.getEventApprovalCoordinator();
        Protocol.EventInterfacePayload iface = coordinator.getInterface(result.eventInstanceId);
        Integer option = coordinator.optionForRequest(result.eventInstanceId, result.requestId);
        if (iface == null || option == null) return;
        String chooser = result.playerId != null ? result.playerId
            : coordinator.chooserForRequest(result.eventInstanceId, result.requestId);
        if (EventRoomOutcomePlanner.requiresLeaveParty(iface, option.intValue())) {
            result.sharedOutcome = EventRoomInstanceRegistry.leavePartyOutcome(option.intValue());
            return;
        }
        if (!EventRoomOutcomePlanner.createsEventRoom(iface, option.intValue())) return;
        EventRoomInstance room = CrossSpireMod.roomHost.getEventRoomInstanceRegistry()
            .allocateOrJoin(party, result.eventInstanceId, option.intValue(), chooser);
        if (room != null) {
            String encounter = EventRoomOutcomePlanner.resolveEncounter(iface, option.intValue());
            result.sharedOutcome = EventRoomInstanceRegistry.toSharedOutcome(room, encounter);
            BaseMod.logger.info("MessageRouter event_room instance=" + room.instanceId
                + " option=" + option + " members=" + room.size()
                + " encounter=" + encounter);
        }
    }

    private void applyPersonalEventResult(Protocol.EventPlayerResultPayload result) {
        if (result != null && result.sharedOutcome != null) {
            applySharedEventOutcome(result.sharedOutcome);
        }
        if (!EventPlayerResultApplyPlanner.shouldApplyLocally(result, CrossSpireMod.playerId)) {
            return;
        }
        if (result.effects == null || result.effects.length == 0) return;
        // Chooser already applied native side effects locally; only re-apply remote/fallback deltas.
        if (result.playerId != null && result.playerId.equals(CrossSpireMod.playerId)
            && !EventOpenModeRegistry.isFallback(result.eventInstanceId)
            && EventOpenModeRegistry.mode(result.eventInstanceId) != null) {
            BaseMod.logger.info("MessageRouter skip re-apply native self result request="
                + result.requestId);
            return;
        }
        resultReplayer.applyEffects(result.effects);
        BaseMod.logger.info("MessageRouter applied personal event result request="
            + result.requestId + " effects=" + result.effects.length);
    }

    /**
     * T7.5: event_room shared_outcome → local STS combat for members on that path.
     * Shop/rest map rooms stay type-only; in-event fight still needs a shared encounter shell.
     */
    private void applySharedEventOutcome(Protocol.SharedOutcome outcome) {
        if (outcome == null || !"event_room".equals(outcome.type)) return;
        if (!EventRoomOutcomePlanner.shouldLocalPlayerEnterEventRoom(outcome, CrossSpireMod.playerId)) {
            BaseMod.logger.info("MessageRouter skip event_room enter not a member instance="
                + outcome.instanceId);
            return;
        }
        String encounter = outcome.encounter != null && !outcome.encounter.isEmpty()
            ? outcome.encounter : "Cultist";
        SyncExecutor.enterEventRoomCombat(outcome.instanceId, encounter);
    }

    private void handleMapHostVote(StandardPacket pkt) {
        if (!CrossSpireMod.isRoomHost() || CrossSpireMod.roomHost == null
            || CrossSpireMod.partyManager == null) {
            return;
        }
        Protocol.MapHostVotePayload vote = Protocol.GSON.fromJson(
            pkt.payload, Protocol.MapHostVotePayload.class);
        if (vote == null) return;
        String partyId = partyId(vote.partyId);
        PartyState party = CrossSpireMod.partyManager.getParty(partyId);
        if (party == null) return;
        if (!CrossSpireMod.roomHost.getPartyHostElectionTracker()
            .castMapHostVote(party, pkt.source, vote.candidateId)) {
            BaseMod.logger.info("MessageRouter reject map_host_vote party=" + partyId);
            return;
        }
        String consensus = CrossSpireMod.roomHost.getPartyHostElectionTracker()
            .mapHostConsensus(party);
        if (consensus != null) {
            StandardPacket result = MapHostVoteSender.buildResult(partyId, consensus);
            CrossSpireMod.sendToParty(partyId, StandardPacket.toJson(result));
            // RoomHost path: register here only. Clients register from map_host_result.
            MapHostRegistrationCoordinator.registerIfElected(partyId, consensus);
            BaseMod.logger.info("MessageRouter map_host_result party=" + partyId
                + " host=" + consensus);
        }
    }

    private void handleMapHostResult(StandardPacket pkt) {
        if (CrossSpireMod.isRoomHost()) return;
        Protocol.MapHostVotePayload result = Protocol.GSON.fromJson(
            pkt.payload, Protocol.MapHostVotePayload.class);
        if (result == null) return;
        String hostId = result.mapHostId != null ? result.mapHostId : result.candidateId;
        if (hostId == null) return;
        MapHostRegistrationCoordinator.registerIfElected(partyId(result.partyId), hostId);
        BaseMod.logger.info("MessageRouter map_host_result party="
            + partyId(result.partyId) + " host=" + hostId);
    }

    private void handleMapRegister(StandardPacket pkt) {
        if (!CrossSpireMod.isRoomHost() || CrossSpireMod.roomHost == null
            || CrossSpireMod.partyManager == null) {
            return;
        }
        Protocol.MapRegistrationPayload registration = Protocol.GSON.fromJson(
            pkt.payload, Protocol.MapRegistrationPayload.class);
        if (registration == null) return;
        String partyId = partyId(registration.partyId);
        PartyState party = CrossSpireMod.partyManager.getParty(partyId);
        MapDefinition map = MapProtocolMapper.fromProtocol(registration.map);
        if (party != null && party.mapInstanceId != null && !party.mapInstanceId.isEmpty()) {
            BaseMod.logger.info("MessageRouter ignore duplicate map_register party=" + partyId
                + " existing=" + party.mapInstanceId);
            return;
        }
        MapDefinition accepted = CrossSpireMod.roomHost.getMapRegistrationCoordinator()
            .register(party, pkt.source, registration.mapHostId, map);
        if (accepted == null) {
            BaseMod.logger.info("MessageRouter reject map_register party=" + partyId);
            return;
        }
        if (!StsMapDefinitionApplier.apply(accepted)) {
            BaseMod.logger.error("MessageRouter RoomHost failed to apply authoritative map="
                + accepted.mapInstanceId);
            return;
        }
        PartyState bound = CrossSpireMod.partyManager.bindMap(
            partyId, accepted.mapInstanceId, accepted.startNodeId, accepted.actId);
        if (bound != null) {
            CrossSpireMod.send(PartySnapshotSender.build());
        }
        StandardPacket registered = MapRegisterSender.buildRegistered(partyId, accepted);
        if (bound != null) {
            registered.payload.addProperty("party_revision", bound.partyRevision);
        }
        CrossSpireMod.sendToParty(partyId, StandardPacket.toJson(registered));
        BaseMod.logger.info("MessageRouter map_registered party=" + partyId
            + " map=" + accepted.mapInstanceId + " start=" + accepted.startNodeId
            + " bound=" + (bound != null));
    }

    private void handleMapRegistered(StandardPacket pkt) {
        if (CrossSpireMod.isRoomHost()) return;
        if (CrossSpireMod.hostId == null || !CrossSpireMod.hostId.equals(pkt.source)) {
            BaseMod.logger.error("MessageRouter reject map_registered from non-host=" + pkt.source);
            return;
        }
        Protocol.MapRegisteredPayload registered = Protocol.GSON.fromJson(
            pkt.payload, Protocol.MapRegisteredPayload.class);
        if (registered == null) return;
        MapDefinition map = MapProtocolMapper.fromProtocol(registered.map);
        if (map == null || !registered.mapInstanceId.equals(map.mapInstanceId)
            || !registered.startNodeId.equals(map.startNodeId)) {
            BaseMod.logger.error("MessageRouter reject map_registered without authoritative topology");
            return;
        }
        if (!StsMapDefinitionApplier.apply(map)) {
            BaseMod.logger.error("MessageRouter failed to reconstruct authoritative map=" + map.mapInstanceId);
            return;
        }
        BaseMod.logger.info("MessageRouter map_registered party="
            + partyId(registered.partyId) + " map=" + registered.mapInstanceId
            + " start=" + registered.startNodeId);
    }

    private void handleNodeInstanceHostVote(StandardPacket pkt) {
        if (!CrossSpireMod.isRoomHost() || CrossSpireMod.roomHost == null
            || CrossSpireMod.partyManager == null) {
            return;
        }
        Protocol.NodeInstanceHostVotePayload vote = Protocol.GSON.fromJson(
            pkt.payload, Protocol.NodeInstanceHostVotePayload.class);
        if (vote == null) return;
        String partyId = partyId(vote.partyId);
        PartyState party = CrossSpireMod.partyManager.getParty(partyId);
        if (party == null || party.mapInstanceId == null || party.mapInstanceId.isEmpty()) {
            BaseMod.logger.info("MessageRouter reject node_instance_host_vote unbound party="
                + partyId);
            return;
        }
        if (!CrossSpireMod.roomHost.getPartyHostElectionTracker()
            .castNodeInstanceHostVote(party, pkt.source, vote.candidateId)) {
            BaseMod.logger.info("MessageRouter reject node_instance_host_vote party=" + partyId);
            return;
        }
        String consensus = CrossSpireMod.roomHost.getPartyHostElectionTracker()
            .nodeInstanceHostConsensus(party);
        if (consensus == null) return;
        PartyState updated = CrossSpireMod.partyManager.setNodeInstanceHost(partyId, consensus);
        if (updated == null) {
            BaseMod.logger.info("MessageRouter reject node_instance_host consensus party="
                + partyId);
            return;
        }
        StandardPacket result = NodeInstanceHostVoteSender.buildResult(partyId, consensus);
        result.payload.addProperty("party_revision", updated.partyRevision);
        CrossSpireMod.sendToParty(partyId, StandardPacket.toJson(result));
        CrossSpireMod.send(PartySnapshotSender.build());
        BaseMod.logger.info("MessageRouter node_instance_host_result party=" + partyId
            + " host=" + consensus);
    }

    private void handleNodeInstanceHostResult(StandardPacket pkt) {
        if (CrossSpireMod.isRoomHost()) return;
        Protocol.NodeInstanceHostVotePayload result = Protocol.GSON.fromJson(
            pkt.payload, Protocol.NodeInstanceHostVotePayload.class);
        if (result == null) return;
        String hostId = result.nodeInstanceHostId != null
            ? result.nodeInstanceHostId : result.candidateId;
        if (hostId == null) return;
        BaseMod.logger.info("MessageRouter node_instance_host_result party="
            + partyId(result.partyId) + " host=" + hostId);
    }

    public void handleEventTranscript(String rawMessage) {
        JsonObject ts = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String eventId = ts.has("event_id") ? ts.get("event_id").getAsString() : "";
        JsonArray actionsArr = ts.has("actions") ? ts.getAsJsonArray("actions") : new JsonArray();

        if (CrossSpireMod.isRoomHost()) {
            String stageHostId = CrossSpireMod.stageHost != null ? CrossSpireMod.stageHost.getStageHostId() : null;
            if (stageHostId != null && !stageHostId.equals(CrossSpireMod.playerId)) {
                CrossSpireMod.connectionManager.send(stageHostId, rawMessage);
                BaseMod.logger.info("MessageRouter forwarded event_transcript to stage host");
                return;
            }
        }

        if (CrossSpireMod.stageHost == null) return;
        if (!CrossSpireMod.stageHost.isStageHost() && !CrossSpireMod.isRoomHost()) return;

        BaseMod.logger.info("MessageRouter event_transcript replay: " + eventId + " actions=" + actionsArr.size());
        if (AbstractDungeon.getCurrRoom() == null || AbstractDungeon.getCurrRoom().event == null) {
            BaseMod.logger.info("MessageRouter event_transcript: no active event");
            return;
        }

        AbstractEvent event = AbstractDungeon.getCurrRoom().event;

        int buttonEffectIdx = -1;
        List<String> cardIds = new ArrayList<>();
        boolean hasConfirm = false;

        for (int i = 0; i < actionsArr.size(); i++) {
            JsonObject action = actionsArr.get(i).getAsJsonObject();
            String actionType = action.has("type") ? action.get("type").getAsString() : "";
            switch (actionType) {
                case "buttonEffect":
                    buttonEffectIdx = action.has("index") ? action.get("index").getAsInt() : 0;
                    break;
                case "cardSelect": {
                    JsonArray cards = action.has("cards") ? action.getAsJsonArray("cards") : new JsonArray();
                    for (int j = 0; j < cards.size(); j++) cardIds.add(cards.get(j).getAsString());
                    break;
                }
                case "confirm": hasConfirm = true; break;
            }
        }

        if (buttonEffectIdx >= 0) {
            try {
                java.lang.reflect.Method m = event.getClass().getDeclaredMethod("buttonEffect", int.class);
                m.setAccessible(true);
                m.invoke(event, buttonEffectIdx);

                if (!cardIds.isEmpty()) {
                    final String targetCard = cardIds.get(0);
                    final boolean doConfirm = hasConfirm;
                    BaseMod.logger.info("MessageRouter queueing cardInject: " + targetCard + " confirm=" + doConfirm);
                    Gdx.app.postRunnable(new Runnable() {
                        @Override public void run() {
                            try {
                                EventSyncPatches.hoverSelectAndConfirm(targetCard);
                            } catch (Throwable t) {
                                BaseMod.logger.error("cardInject error: " + t.getClass().getName() + ": " + t.getMessage());
                            }
                        }
                    });
                }
                BaseMod.logger.info("MessageRouter event_transcript buttonEffect: " + buttonEffectIdx
                    + " +cards=" + cardIds.size() + " +confirm=" + hasConfirm);
            } catch (Exception e) {
                BaseMod.logger.error("MessageRouter event_transcript buttonEffect failed: " + e.getMessage());
            }
        } else if (!cardIds.isEmpty()) {
            final String targetCard = cardIds.get(0);
            final boolean doConfirm = hasConfirm;
            Gdx.app.postRunnable(new Runnable() {
                @Override public void run() {
                    if (doConfirm) {
                        EventSyncPatches.hoverSelectAndConfirm(targetCard);
                    } else {
                        EventSyncPatches.hoverSelectCard(targetCard);
                    }
                }
            });
            BaseMod.logger.info("MessageRouter event_transcript standalone cards: "
                + cardIds.size() + " confirm=" + hasConfirm);
        }
    }

    private void handleEventVote(String rawMessage) {
        JsonObject v = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String source = v.has("source") ? v.get("source").getAsString() : "";
        int idx = v.has("option_index") ? v.get("option_index").getAsInt() : -1;
        if (source.isEmpty() || idx < 0) return;

        if (!CrossSpireMod.isRoomHost()) {
            CrossSpireMod.send((String) rawMessage);
            return;
        }

        if (CrossSpireMod.roomHost != null) {
            CrossSpireMod.roomHost.castEventVote(source, idx);
            Integer consensus = CrossSpireMod.roomHost.checkEventVoteConsensus();
            if (consensus != null) {
                BaseMod.logger.info("MessageRouter event_vote consensus: option=" + consensus);
                EventCapture.startTranscript("event-vote-consensus");
                EventCapture.appendButtonEffect(consensus);
                String transcript = EventCapture.buildTranscript();
                handleEventTranscript(transcript);
            } else {
                String votesJson = CrossSpireMod.roomHost.getEventVotesJson();
                com.google.gson.JsonObject votesMsg = new com.google.gson.JsonObject();
                votesMsg.addProperty("type", "event_votes");
                votesMsg.addProperty("source", CrossSpireMod.playerId);
                votesMsg.add("votes", Protocol.GSON.fromJson(votesJson, com.google.gson.JsonObject.class));
                CrossSpireMod.send(Protocol.GSON.toJson(votesMsg));
                BaseMod.logger.info("MessageRouter event_votes broadcast: " + votesJson);
            }
        }
    }

    private void handleRewardPhaseEnter(String rawMessage) {
        Protocol.RewardPhaseEnter msg =
            Protocol.GSON.fromJson(rawMessage, Protocol.RewardPhaseEnter.class);
        if (msg == null) return;
        String partyId = partyId(msg.partyId);
        if (!isLocalParty(partyId)) return;
        if (CrossSpireMod.rewardPhaseTracker != null) {
            if (CrossSpireMod.rewardPhaseTracker.isCompletedNode(msg.nodeInstanceId)) {
                BaseMod.logger.info("MessageRouter ignore reward_phase_enter completed node="
                    + msg.nodeInstanceId);
                return;
            }
            if (!CrossSpireMod.rewardPhaseTracker.enter(partyId, msg.nodeInstanceId)) {
                BaseMod.logger.info("MessageRouter ignore reward_phase_enter party=" + partyId
                    + " node=" + msg.nodeInstanceId);
                return;
            }
        }
        ActiveNodeTracker.setRewardPhaseActive(true);
        BaseMod.logger.info("MessageRouter reward_phase_enter party=" + partyId
            + " node=" + msg.nodeInstanceId);
    }

    private void handleRewardDone(String rawMessage) {
        Protocol.RewardDone msg = Protocol.GSON.fromJson(rawMessage, Protocol.RewardDone.class);
        if (msg == null || msg.source == null) return;
        String partyId = partyId(msg.partyId);
        PartyState party = CrossSpireMod.partyManager != null
            ? CrossSpireMod.partyManager.getParty(partyId) : null;
        if (party == null) return;

        if (CrossSpireMod.isRoomHost()
            && !PartyCoordinator.isLeader(CrossSpireMod.partyManager, partyId, CrossSpireMod.playerId)) {
            String leaderId = PartyCoordinator.leaderId(CrossSpireMod.partyManager, partyId);
            if (leaderId != null && !leaderId.isEmpty() && !leaderId.equals(CrossSpireMod.playerId)) {
                CrossSpireMod.sendToPlayer(leaderId, rawMessage);
                return;
            }
        }

        if (!PartyCoordinator.isLeader(CrossSpireMod.partyManager, partyId, CrossSpireMod.playerId)
            && !CrossSpireMod.isRoomHost()) {
            CrossSpireMod.send(rawMessage);
            return;
        }

        if (CrossSpireMod.rewardPhaseTracker == null) return;
        // Never re-open a completed reward node on late reward_done.
        if (CrossSpireMod.rewardPhaseTracker.isCompletedNode(msg.nodeInstanceId)) {
            BaseMod.logger.info("MessageRouter ignore late reward_done completed node="
                + msg.nodeInstanceId);
            return;
        }
        if (!CrossSpireMod.rewardPhaseTracker.isActive()) {
            // First done can open phase only if enter was lost; not if already completed.
            if (!CrossSpireMod.rewardPhaseTracker.enter(partyId, msg.nodeInstanceId)) {
                return;
            }
            ActiveNodeTracker.setRewardPhaseActive(true);
        }
        boolean allDone = CrossSpireMod.rewardPhaseTracker.markDone(msg.source, party);
        BaseMod.logger.info("MessageRouter reward_done from="
            + (msg.source.length() >= 8 ? msg.source.substring(0, 8) : msg.source)
            + " done=" + CrossSpireMod.rewardPhaseTracker.doneCount()
            + "/" + party.memberIds.size());
        if (allDone) {
            Protocol.RewardPhaseComplete complete = new Protocol.RewardPhaseComplete();
            complete.source = CrossSpireMod.playerId;
            complete.seq = CrossSpireMod.nextSeq();
            complete.partyId = partyId;
            complete.nodeInstanceId = msg.nodeInstanceId != null
                ? msg.nodeInstanceId : CrossSpireMod.rewardPhaseTracker.getNodeInstanceId();
            String json = Protocol.GSON.toJson(complete);
            CrossSpireMod.send(json);
            handleRewardPhaseComplete(json);
        }
    }

    private void handleRewardPhaseComplete(String rawMessage) {
        Protocol.RewardPhaseComplete msg =
            Protocol.GSON.fromJson(rawMessage, Protocol.RewardPhaseComplete.class);
        if (msg == null) return;
        String partyId = partyId(msg.partyId);
        if (!isLocalParty(partyId)) return;
        if (CrossSpireMod.rewardPhaseTracker != null) {
            if (CrossSpireMod.rewardPhaseTracker.isCompletedNode(msg.nodeInstanceId)
                && !CrossSpireMod.rewardPhaseTracker.isActive()) {
                ActiveNodeTracker.setRewardPhaseActive(false);
                applyRoomExitUnlocked(partyId, msg.nodeInstanceId, "reward_complete");
                BaseMod.logger.info("MessageRouter reward_phase_complete idempotent party="
                    + partyId + " node=" + msg.nodeInstanceId);
                return;
            }
            CrossSpireMod.rewardPhaseTracker.clear();
        }
        ActiveNodeTracker.setRewardPhaseActive(false);
        applyRoomExitUnlocked(partyId, msg.nodeInstanceId, "reward_complete");
        BaseMod.logger.info("MessageRouter reward_phase_complete party=" + partyId
            + " node=" + msg.nodeInstanceId + " room_pin allowed");
    }

    private void handleRoomExitUnlocked(String rawMessage) {
        Protocol.RoomExitUnlocked msg =
            Protocol.GSON.fromJson(rawMessage, Protocol.RoomExitUnlocked.class);
        if (msg == null) return;
        String partyId = partyId(msg.partyId);
        if (!isLocalParty(partyId)) return;
        String instanceId = msg.roomInstanceId != null && !msg.roomInstanceId.isEmpty()
            ? msg.roomInstanceId : msg.nodeInstanceId;
        applyRoomExitUnlocked(partyId, instanceId, msg.reason != null ? msg.reason : "host");
    }

    private void handleRoomExitLocked(String rawMessage) {
        Protocol.RoomExitLocked msg =
            Protocol.GSON.fromJson(rawMessage, Protocol.RoomExitLocked.class);
        if (msg == null || CrossSpireMod.roomNavigationGate == null) return;
        String partyId = partyId(msg.partyId);
        if (!isLocalParty(partyId)) return;
        String instanceId = msg.roomInstanceId != null && !msg.roomInstanceId.isEmpty()
            ? msg.roomInstanceId : msg.nodeInstanceId;
        CrossSpireMod.roomNavigationGate.lock(partyId, instanceId);
        BaseMod.logger.info("MessageRouter room_exit_locked party=" + partyId
            + " room=" + instanceId);
    }

    private static void applyRoomExitUnlocked(String partyId, String roomInstanceId, String reason) {
        if (CrossSpireMod.roomNavigationGate == null) return;
        boolean ok = CrossSpireMod.roomNavigationGate.unlock(partyId, roomInstanceId, reason);
        BaseMod.logger.info("MessageRouter room_exit_unlocked party=" + partyId
            + " room=" + roomInstanceId + " reason=" + reason + " applied=" + ok
            + " " + CrossSpireMod.roomNavigationGate.summary());
    }

    /**
     * RoomInstanceHost (or console): broadcast map navigation unlock for the active room.
     * Shop/rest do not sync inventory/options—only unlock so the party can pin.
     */
    public static void broadcastRoomExitUnlocked(String reason) {
        String partyId = ActiveNodeTracker.getPartyId();
        if (partyId == null || partyId.isEmpty()) partyId = PartyManager.DEFAULT_PARTY_ID;
        String instanceId = ActiveNodeTracker.getNodeInstanceId();
        if (instanceId == null || instanceId.isEmpty()) {
            if (CrossSpireMod.roomNavigationGate != null) {
                instanceId = CrossSpireMod.roomNavigationGate.getRoomInstanceId();
            }
        }
        Protocol.RoomExitUnlocked msg = new Protocol.RoomExitUnlocked();
        msg.source = CrossSpireMod.playerId;
        msg.seq = CrossSpireMod.nextSeq();
        msg.partyId = partyId;
        msg.roomInstanceId = instanceId != null ? instanceId : "";
        msg.nodeInstanceId = msg.roomInstanceId;
        msg.reason = reason != null ? reason : "manual";
        String json = Protocol.GSON.toJson(msg);
        CrossSpireMod.send(json);
        if (CrossSpireMod.messageRouter != null) {
            CrossSpireMod.messageRouter.route(json);
        } else {
            applyRoomExitUnlocked(partyId, msg.roomInstanceId, msg.reason);
        }
        BaseMod.logger.info("MessageRouter broadcast room_exit_unlocked reason=" + msg.reason
            + " room=" + msg.roomInstanceId);
    }
}
