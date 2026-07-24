package crossspire.sync;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;
import crossspire.combat.EventSyncPatches;
import crossspire.event.NativeEventApprovalPatches;
import crossspire.event.NativeEventOpenPlanner;
import crossspire.network.Protocol;
import crossspire.map.MapDefinition;
import crossspire.map.StsMapDefinitionApplier;
import crossspire.map.StsMapTopology;
import crossspire.party.ActiveNodeTracker;
import crossspire.reference.ContentValidator;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.remote.RemotePlayerState;
import crossspire.ui.RemoteEventDisplay;

public class SyncExecutor {

    public void handleSync(String subtype, String source, int seq, String rawMessage) {
        if ("remote_player".equals(subtype)) {
            handleRemotePlayerSync(rawMessage);
        } else if ("battle_start".equals(subtype)) {
            handleBattleStart(rawMessage);
        } else if ("room_enter".equals(subtype)) {
            handleRoomEnter(rawMessage);
        } else if ("monster_intent".equals(subtype)) {
            handleMonsterIntent(rawMessage);
        }
    }

    private void handleMonsterIntent(String rawMessage) {
        Protocol.MonsterIntentMessage msg = Protocol.GSON.fromJson(rawMessage, Protocol.MonsterIntentMessage.class);
        if (msg.intents != null && msg.intents.length > 0) {
            IntentRenderer.showSnapshot(msg.intents);
        } else {
            IntentRenderer.show(msg.monsterId, msg.intent, msg.damage, msg.hits);
        }
    }

    public void handleCombatResult(String rawMessage) {
        Protocol.CombatResultMessage msg = Protocol.GSON.fromJson(rawMessage, Protocol.CombatResultMessage.class);
        CrossSpireMod.messageRouter.replayCombatResult(msg.effects);
    }

    public void handleEventResult(String rawMessage) {
        Protocol.EventResultMessage msg = Protocol.GSON.fromJson(rawMessage, Protocol.EventResultMessage.class);
        if (msg.effects != null && msg.effects.length > 0) {
            CrossSpireMod.messageRouter.replayCombatResult(msg.effects);
        }
    }

    private void handleBattleStart(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String seed = msg.has("seed") ? msg.get("seed").getAsString() : "";
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        if (!source.isEmpty() && CrossSpireMod.stageHost != null) {
            CrossSpireMod.stageHost.setStageHost(source);
        }
        if (source.equals(CrossSpireMod.playerId)) return;
    }

    private void handleRemotePlayerSync(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        if (!msg.has("player")) return;
        JsonObject p = msg.getAsJsonObject("player");
        RemotePlayerState rp = RemotePlayerRegistry.get(source);
        if (rp == null) { RemotePlayerRegistry.register(source); rp = RemotePlayerRegistry.get(source); }
        if (rp == null) return;
        if (p.has("hp")) rp.hp = p.get("hp").getAsInt();
        if (p.has("max_hp")) rp.maxHp = p.get("max_hp").getAsInt();
        if (p.has("block")) rp.block = p.get("block").getAsInt();
        if (p.has("hp") || p.has("block")) {
            BaseMod.logger.info("SyncExecutor remote HP: " + source.substring(0, 8)
                + " hp=" + rp.hp + "/" + rp.maxHp + " block=" + rp.block);
        }
        if (p.has("gold")) rp.gold = p.get("gold").getAsInt();
        if (p.has("energy")) rp.energy = p.get("energy").getAsInt();
        if (p.has("character_class")) rp.characterClass = p.get("character_class").getAsString();
        if (p.has("powers") && p.has("power_amounts")) {
            JsonArray pw = p.getAsJsonArray("powers");
            JsonArray pa = p.getAsJsonArray("power_amounts");
            int len = Math.min(pw.size(), pa.size());
            rp.powers = new String[len];
            rp.powerAmounts = new int[len];
            for (int i = 0; i < len; i++) { rp.powers[i] = pw.get(i).getAsString(); rp.powerAmounts[i] = pa.get(i).getAsInt(); }
        }
    }

    private void handleRoomEnter(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        if (source.equals(CrossSpireMod.playerId)) return;
        JsonArray ids = msg.has("monster_ids") ? msg.getAsJsonArray("monster_ids") : new JsonArray();
        if (ids.size() == 0) return;

        final String firstMonster = ids.get(0).getAsString();
        BaseMod.logger.info("SyncExecutor room_enter from " + source.substring(0,8) + " monsters=" + ids);

        CombatSyncPatches.suppressBroadcast = true;

        // GameStarter uses menu fade-out; player/dungeon exist only after CardCrawlGame.update.
        // Retry a few frames so remote clients are not stuck without a MonsterRoom.
        scheduleRemoteCombatEntry(firstMonster, 0);
    }

    private void scheduleRemoteCombatEntry(final String monsterName, final int attempt) {
        Gdx.app.postRunnable(new Runnable() {
            @Override public void run() {
                createGameIfNeeded();
                crossspire.remote.GameStarter.bindLocalPlayerIfReady();

                if (AbstractDungeon.player == null
                        || CardCrawlGame.mode != CardCrawlGame.GameMode.GAMEPLAY
                        || AbstractDungeon.getCurrMapNode() == null) {
                    if (attempt < 12) {
                        scheduleRemoteCombatEntry(monsterName, attempt + 1);
                    } else {
                        BaseMod.logger.error("SyncExecutor room_enter: game not ready after retries");
                    }
                    return;
                }

                EventSuppression.suppressEvents(() -> {
                    enterRemoteCombat(monsterName);
                });
                BaseMod.logger.info("SyncExecutor done: attempt=" + attempt
                    + " screen=" + AbstractDungeon.screen
                    + " room=" + (AbstractDungeon.getCurrRoom() != null
                        ? AbstractDungeon.getCurrRoom().getClass().getSimpleName() : "null"));
            }
        });
    }

    /**
     * Legacy host-spawn combat projection fallback only.
     * Co-op main path (T7.7a): clients must already be in GAMEPLAY via party_run_start.
     * Do not use this as the dual-client open-world start.
     */
    private static void createGameIfNeeded() {
        if (AbstractDungeon.player != null && CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY) {
            BaseMod.logger.info("SyncExecutor game already running, skip create");
            return;
        }
        if (CrossSpireMod.lobbyState != null && CrossSpireMod.lobbyState.isStarted()) {
            BaseMod.logger.info("SyncExecutor skip createGameIfNeeded: party_run_start already applied"
                + " (wait for local GAMEPLAY; co-op path must not force IRONCLAD)");
            return;
        }
        BaseMod.logger.info("SyncExecutor createGameIfNeeded legacy IRONCLAD bootstrap"
            + " (host-spawn projection only; not co-op main path)");
        crossspire.remote.GameStarter.start("IRONCLAD");
    }

    /**
     * T7.5: open a shared in-event combat shell for members on an event_room path.
     * Uses the current map node (event node) as host for a MonsterRoom install.
     */
    public static void enterEventRoomCombat(String eventRoomInstanceId, String encounter) {
        final String enc = encounter != null && !encounter.isEmpty() ? encounter : "Cultist";
        final String instanceId = eventRoomInstanceId != null ? eventRoomInstanceId : "";
        Gdx.app.postRunnable(new Runnable() {
            @Override public void run() {
                if (AbstractDungeon.player == null
                        || CardCrawlGame.mode != CardCrawlGame.GameMode.GAMEPLAY) {
                    BaseMod.logger.info("SyncExecutor event_room combat deferred: not GAMEPLAY");
                    return;
                }
                BaseMod.logger.info("SyncExecutor event_room combat enter instance="
                    + instanceId + " encounter=" + enc);
                // Tag active tracker so combat_result / reward paths see a room id.
                ActiveNodeTracker.setActive(
                    ActiveNodeTracker.getPartyId().isEmpty()
                        ? crossspire.party.PartyManager.DEFAULT_PARTY_ID
                        : ActiveNodeTracker.getPartyId(),
                    ActiveNodeTracker.getMapInstanceId(),
                    ActiveNodeTracker.getNodeId(),
                    instanceId.isEmpty() ? ActiveNodeTracker.getNodeInstanceId() : instanceId,
                    "monster",
                    enc);
                if (CrossSpireMod.roomNavigationGate != null && !instanceId.isEmpty()) {
                    CrossSpireMod.roomNavigationGate.onRoomOpened(
                        ActiveNodeTracker.getPartyId(), instanceId);
                }
                EventSuppression.suppressEvents(new Runnable() {
                    @Override public void run() {
                        enterRemoteCombat(enc);
                    }
                });
            }
        });
    }

    private static void enterRemoteCombat(String monsterName) {
        if (AbstractDungeon.getCurrRoom() instanceof MonsterRoom
                && AbstractDungeon.getCurrRoom().monsters != null) {
            BaseMod.logger.info("SyncExecutor already in combat, skip");
            return;
        }

        String key = resolveEncounterKey(monsterName);
        BaseMod.logger.info("SyncExecutor entering combat: " + monsterName + " key=" + key);

        try {
            if (AbstractDungeon.getCurrMapNode() == null) {
                BaseMod.logger.error("SyncExecutor enterRemoteCombat: no map node");
                return;
            }

            MonsterGroup group = MonsterHelper.getEncounter(key);
            MonsterRoom room = new MonsterRoom();
            room.monsters = group;

            AbstractDungeon.nextRoom = null;
            AbstractDungeon.getCurrMapNode().room = room;
            AbstractDungeon.screen = com.megacrit.cardcrawl.dungeons.AbstractDungeon.CurrentScreen.NONE;
            room.onPlayerEntry();
            AbstractDungeon.nextRoom = null;
            if (room.monsters != null) {
                for (com.megacrit.cardcrawl.monsters.AbstractMonster m : room.monsters.monsters) {
                    if (!m.isDeadOrEscaped()) {
                        m.showHealthBar();
                    }
                }
            }

            BaseMod.logger.info("SyncExecutor combat entered: " + monsterName);
        } catch (Exception e) {
            BaseMod.logger.error("SyncExecutor enterRemoteCombat: " + e.getClass().getSimpleName()
                + ": " + e.getMessage());
        }
    }

    private static String resolveEncounterKey(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("cultist")) return "Cultist";
        if (lower.contains("jaw worm")) return "Jaw Worm";
        if (lower.contains("looter")) return "Looter";
        if (lower.contains("blue slaver")) return "Blue Slaver";
        if (lower.contains("red slaver")) return "Red Slaver";
        if (lower.contains("fungi beast")) return "Fungi Beast";
        if (lower.contains("gremlin nob")) return "Gremlin Nob";
        if (lower.contains("lagavulin")) return "Lagavulin";
        if (lower.contains("sentry")) return "Sentry";
        if (lower.contains("slime boss")) return "Slime Boss";
        if (lower.contains("guardian")) return "The Guardian";
        if (lower.contains("hexaghost")) return "Hexaghost";
        return name;
    }

    public static void executeRoomConsensus(int roomIndex) {
        BaseMod.logger.info("SyncExecutor executeRoomConsensus: room=" + roomIndex);
        if (!CrossSpireMod.stageHost.isStageHost()) {
            BaseMod.logger.info("SyncExecutor not stage host, ignoring room_consensus");
            return;
        }
        Gdx.app.postRunnable(new Runnable() {
            @Override public void run() {
                enterRemoteCombat("Cultist");
            }
        });
    }

    /** Opens an authorized node instance without re-running generation. */
    public static void openNodeInstanceAsHost(Protocol.NodeInstanceInfo info,
                                               Protocol.NodeGenerationResult result) {
        openNodeInstance(info, result, true);
    }

    /** Party members apply RoomHost node_instance_opened without re-running generation. */
    public static void openNodeInstanceAsMember(Protocol.NodeInstanceInfo info,
                                                Protocol.NodeGenerationResult result) {
        openNodeInstance(info, result, false);
    }

    private static void openNodeInstance(Protocol.NodeInstanceInfo info,
                                         Protocol.NodeGenerationResult result,
                                         boolean asHost) {
        if (info == null || result == null) return;
        final String role = asHost ? "Host" : "Member";
        String encounterOrType = result.encounter != null ? result.encounter : result.roomType;
        // Force-follow: drop prior room identity before installing the new RoomInstance.
        String prev = ActiveNodeTracker.getNodeInstanceId();
        if (prev != null && !prev.isEmpty() && !prev.equals(info.nodeInstanceId)) {
            ActiveNodeTracker.forceLeave("opened " + info.nodeInstanceId);
        }
        ActiveNodeTracker.setActive(info.partyId, info.mapInstanceId, info.nodeId,
            info.nodeInstanceId, result.roomType, encounterOrType);
        if (CrossSpireMod.roomNavigationGate != null) {
            CrossSpireMod.roomNavigationGate.onRoomOpened(info.partyId, info.nodeInstanceId);
        }
        BaseMod.logger.info("SyncExecutor active node " + ActiveNodeTracker.summary()
            + " role=" + role + " nav locked");
        if ("monster".equals(result.roomType) || "elite".equals(result.roomType)
            || "boss".equals(result.roomType)) {
            final String encounter = result.encounter != null && !result.encounter.isEmpty()
                ? result.encounter
                : ("boss".equals(result.roomType) ? "The Guardian"
                    : ("elite".equals(result.roomType) ? "Gremlin Nob" : "Cultist"));
            final String nodeId = info.nodeId;
            BaseMod.logger.info("SyncExecutor openNodeInstanceAs" + role + " party=" + info.partyId
                + " node=" + nodeId + " type=" + result.roomType + " encounter=" + encounter
                + " instance=" + info.nodeInstanceId);
            scheduleAuthoritativeMonsterEntry(nodeId, encounter, 0);
            return;
        }
        if ("event".equals(result.roomType)) {
            final Protocol.EventInterfacePayload iface = result.eventInterface;
            BaseMod.logger.info("SyncExecutor openNodeInstanceAs" + role + " party=" + info.partyId
                + " node=" + info.nodeId + " event="
                + (iface != null ? iface.eventInstanceId : "?"));
            Gdx.app.postRunnable(new Runnable() {
                @Override public void run() {
                    EventSuppression.suppressEvents(new Runnable() {
                        @Override public void run() {
                            enterRemoteEvent(iface);
                        }
                    });
                }
            });
            return;
        }
        if ("shop".equals(result.roomType) || "rest".equals(result.roomType)
            || "treasure".equals(result.roomType)) {
            final String roomType = result.roomType;
            final String nodeId = info.nodeId;
            BaseMod.logger.info("SyncExecutor openNodeInstanceAs" + role + " party=" + info.partyId
                + " node=" + nodeId + " type=" + roomType);
            scheduleAuthoritativeRoomEntry(nodeId, roomType, 0);
        }
    }

    private static void scheduleAuthoritativeRoomEntry(final String nodeId, final String roomType,
                                                         final int attempt) {
        Gdx.app.postRunnable(new Runnable() {
            @Override public void run() {
                createGameIfNeeded();
                crossspire.remote.GameStarter.bindLocalPlayerIfReady();
                boolean ready = AbstractDungeon.player != null
                    && CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY
                    && AbstractDungeon.getCurrMapNode() != null
                    && StsMapDefinitionApplier.active() != null
                    && CardCrawlGame.dungeon != null;
                if (!ready) {
                    if (attempt < 40) {
                        scheduleAuthoritativeRoomEntry(nodeId, roomType, attempt + 1);
                    } else {
                        BaseMod.logger.error("SyncExecutor room open not ready after retries node="
                            + nodeId + " type=" + roomType);
                    }
                    return;
                }
                MapDefinition active = StsMapDefinitionApplier.active();
                if (active != null) StsMapDefinitionApplier.apply(active);
                EventSuppression.suppressEvents(new Runnable() {
                    @Override public void run() {
                        enterAuthoritativeTypedRoom(nodeId, roomType);
                    }
                });
            }
        });
    }

    private static void enterAuthoritativeTypedRoom(String nodeId, String roomType) {
        MapDefinition active = StsMapDefinitionApplier.active();
        MapRoomNode target = StsMapDefinitionApplier.find(nodeId);
        MapRoomNode current = AbstractDungeon.getCurrMapNode();
        if (target == null) {
            BaseMod.logger.error("SyncExecutor reject room node missing " + nodeId + " type=" + roomType);
            return;
        }
        String fromId = resolveCurrentNodeId(current, active);
        if (target != current && !StsMapDefinitionApplier.isReachable(fromId, nodeId)) {
            BaseMod.logger.error("SyncExecutor reject unreachable room node=" + nodeId
                + " type=" + roomType + " from=" + fromId);
            return;
        }
        try {
            target.setRoom(createRoom(roomType));
            CombatSyncPatches.suppressBroadcast = true;
            if (CardCrawlGame.dungeon != null && AbstractDungeon.overlayMenu != null
                && AbstractDungeon.fadeColor != null) {
                AbstractDungeon.nextRoom = target;
                CardCrawlGame.dungeon.nextRoomTransition();
            } else {
                AbstractDungeon.currMapNode = target;
                if (target.room != null) target.room.onPlayerEntry();
            }
            BaseMod.logger.info("SyncExecutor transition to authoritative room node=" + nodeId
                + " type=" + roomType + " from=" + fromId);
        } catch (Exception e) {
            CombatSyncPatches.suppressBroadcast = false;
            BaseMod.logger.error("SyncExecutor authoritative room transition: "
                + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static com.megacrit.cardcrawl.rooms.AbstractRoom createRoom(String roomType) {
        if ("shop".equals(roomType)) return new com.megacrit.cardcrawl.rooms.ShopRoom();
        if ("rest".equals(roomType)) return new com.megacrit.cardcrawl.rooms.RestRoom();
        if ("treasure".equals(roomType)) return new com.megacrit.cardcrawl.rooms.TreasureRoom();
        if ("elite".equals(roomType)) return new com.megacrit.cardcrawl.rooms.MonsterRoomElite();
        if ("boss".equals(roomType)) return new com.megacrit.cardcrawl.rooms.MonsterRoomBoss();
        if ("event".equals(roomType)) return new com.megacrit.cardcrawl.rooms.EventRoom();
        return new MonsterRoom();
    }

    private static void scheduleAuthoritativeMonsterEntry(final String nodeId, final String encounter,
                                                            final int attempt) {
        Gdx.app.postRunnable(new Runnable() {
            @Override public void run() {
                createGameIfNeeded();
                crossspire.remote.GameStarter.bindLocalPlayerIfReady();
                boolean ready = AbstractDungeon.player != null
                    && CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY
                    && AbstractDungeon.getCurrMapNode() != null
                    && StsMapDefinitionApplier.active() != null
                    && CardCrawlGame.dungeon != null;
                if (!ready) {
                    if (attempt < 40) {
                        scheduleAuthoritativeMonsterEntry(nodeId, encounter, attempt + 1);
                    } else {
                        BaseMod.logger.error("SyncExecutor authoritative open not ready after retries node="
                            + nodeId + " mode=" + CardCrawlGame.mode
                            + " dungeon=" + (CardCrawlGame.dungeon != null)
                            + " activeMap=" + (StsMapDefinitionApplier.active() != null));
                    }
                    return;
                }
                // Local bootstrap may have regenerated map; ensure authoritative topology is current.
                MapDefinition active = StsMapDefinitionApplier.active();
                if (active != null) StsMapDefinitionApplier.apply(active);
                EventSuppression.suppressEvents(new Runnable() {
                    @Override public void run() {
                        enterAuthoritativeMonsterNode(nodeId, encounter);
                    }
                });
            }
        });
    }

    /** Installs NIH-owned combat content in a reconstructed map node, then uses STS transition flow. */
    private static void enterAuthoritativeMonsterNode(String nodeId, String encounter) {
        MapDefinition active = StsMapDefinitionApplier.active();
        MapRoomNode target = StsMapDefinitionApplier.find(nodeId);
        MapRoomNode current = AbstractDungeon.getCurrMapNode();
        if (target == null || target.room == null || !(target.room instanceof MonsterRoom)) {
            BaseMod.logger.error("SyncExecutor reject monster node: missing or non-monster " + nodeId
                + " target=" + target + " room=" + (target != null ? target.room : null));
            return;
        }
        String fromId = resolveCurrentNodeId(current, active);
        if (target != current && !StsMapDefinitionApplier.isReachable(fromId, nodeId)) {
            BaseMod.logger.error("SyncExecutor reject unreachable monster node=" + nodeId
                + " from=" + fromId + " curr=" + (current != null
                    ? current.x + ":" + current.y : "null"));
            return;
        }
        try {
            String key = resolveEncounterKey(encounter);
            if (AbstractDungeon.lastCombatMetricKey == null) {
                AbstractDungeon.lastCombatMetricKey = key;
            }
            ((MonsterRoom) target.room).monsters = null;
            CombatSyncPatches.suppressBroadcast = true;
            // Prefer STS transition when dungeon fade/overlay is fully live; otherwise enter directly.
            if (CardCrawlGame.dungeon != null && AbstractDungeon.overlayMenu != null
                && AbstractDungeon.fadeColor != null) {
                AbstractDungeon.nextRoom = target;
                CardCrawlGame.dungeon.nextRoomTransition();
            } else {
                enterRemoteCombat(key);
                AbstractDungeon.currMapNode = target;
            }
            BaseMod.logger.info("SyncExecutor transition to authoritative monster node=" + nodeId
                + " from=" + fromId + " key=" + key);
        } catch (Exception e) {
            CombatSyncPatches.suppressBroadcast = false;
            BaseMod.logger.error("SyncExecutor authoritative monster transition: "
                + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            try {
                enterRemoteCombat(resolveEncounterKey(encounter));
                AbstractDungeon.currMapNode = target;
            } catch (Exception fallback) {
                BaseMod.logger.error("SyncExecutor direct combat fallback failed: "
                    + fallback.getClass().getSimpleName() + ": " + fallback.getMessage(), fallback);
            }
        }
    }

    private static String resolveCurrentNodeId(MapRoomNode current, MapDefinition active) {
        if (active == null) {
            return current != null ? StsMapTopology.nodeId(current.x, current.y) : null;
        }
        // Party still at map start while STS may still be on Neow or another off-topology room.
        if (current == null) return active.startNodeId;
        for (crossspire.map.MapNode node : active.nodes()) {
            if (node.x == current.x && node.y == current.y) return node.nodeId;
        }
        if (CrossSpireMod.partyManager != null) {
            String partyId = CrossSpireMod.partyManager.getPartyIdForPlayer(CrossSpireMod.playerId);
            crossspire.party.PartyState party = partyId != null
                ? CrossSpireMod.partyManager.getParty(partyId) : null;
            if (party != null && party.mapPosition != null && !party.mapPosition.isEmpty()) {
                return party.mapPosition;
            }
        }
        return active.startNodeId;
    }

    /**
     * Opens a party event: native class/hash match binds approval gate; otherwise fallback UI.
     * Does not execute NIH fallback resolution yet.
     */
    public static void enterRemoteEvent(Protocol.EventInterfacePayload iface) {
        if (iface == null) return;
        String localHash = ContentValidator.hashClass(iface.eventClass);
        boolean classPresent = canLoadEventClass(iface.eventClass);
        NativeEventOpenPlanner.Mode mode = NativeEventOpenPlanner.mode(iface, classPresent, localHash);
        if (mode == NativeEventOpenPlanner.Mode.NATIVE) {
            if (enterNativeEvent(iface)) {
                crossspire.event.EventOpenModeRegistry.mark(
                    iface.eventInstanceId, crossspire.event.EventOpenModeRegistry.NATIVE);
                BaseMod.logger.info("SyncExecutor native event opened: " + iface.eventInstanceId);
                return;
            }
        }
        crossspire.event.EventOpenModeRegistry.mark(
            iface.eventInstanceId, crossspire.event.EventOpenModeRegistry.FALLBACK);
        RemoteEventDisplay.show(iface);
        BaseMod.logger.info("SyncExecutor fallback event display: " + iface.eventInstanceId
            + " classPresent=" + classPresent);
    }

    private static boolean enterNativeEvent(Protocol.EventInterfacePayload iface) {
        if (AbstractDungeon.getCurrMapNode() == null) {
            BaseMod.logger.error("SyncExecutor enterRemoteEvent: no map node");
            return false;
        }
        AbstractEvent event = instantiateEvent(iface.eventClass);
        if (event == null) return false;
        try {
            EventRoom room = new EventRoom();
            room.event = event;
            AbstractDungeon.nextRoom = null;
            AbstractDungeon.getCurrMapNode().room = room;
            AbstractDungeon.screen = AbstractDungeon.CurrentScreen.NONE;
            if (AbstractDungeon.effectList != null) AbstractDungeon.effectList.clear();
            if (AbstractDungeon.topLevelEffects != null) AbstractDungeon.topLevelEffects.clear();
            EventSyncPatches.suppressBroadcast = true;
            try {
                event.onEnterRoom();
            } finally {
                EventSyncPatches.suppressBroadcast = false;
            }
            AbstractDungeon.nextRoom = null;
            RemoteEventDisplay.hide();
            NativeEventApprovalPatches.bind(event, iface);
            return true;
        } catch (Exception e) {
            BaseMod.logger.error("SyncExecutor enterNativeEvent: " + e.getClass().getSimpleName()
                + ": " + e.getMessage());
            return false;
        }
    }

    private static AbstractEvent instantiateEvent(String eventClass) {
        if (eventClass == null || eventClass.isEmpty()) return null;
        try {
            Class<?> cls = Class.forName(eventClass);
            java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (AbstractEvent) ctor.newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean canLoadEventClass(String eventClass) {
        if (eventClass == null || eventClass.isEmpty()) return false;
        try {
            Class.forName(eventClass);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void handleFullSnapshot(String rawMessage) {
        JsonObject snap = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String source = snap.has("source") ? snap.get("source").getAsString() : "";
        if (source.equals(CrossSpireMod.playerId)) return;
        BaseMod.logger.info("SyncExecutor full_snapshot from " + source.substring(0, 8));
        handleRemotePlayerSync(rawMessage);
    }
}
