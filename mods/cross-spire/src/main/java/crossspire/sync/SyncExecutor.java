package crossspire.sync;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
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

    private void createGameIfNeeded() {
        if (AbstractDungeon.player != null && CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY) {
            BaseMod.logger.info("SyncExecutor game already running, skip create");
            return;
        }
        crossspire.remote.GameStarter.start("IRONCLAD");
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
        if ("monster".equals(result.roomType)) {
            final String encounter = result.encounter != null && !result.encounter.isEmpty()
                ? result.encounter : "Cultist";
            BaseMod.logger.info("SyncExecutor openNodeInstanceAs" + role + " party=" + info.partyId
                + " node=" + info.nodeId + " encounter=" + encounter);
            Gdx.app.postRunnable(new Runnable() {
                @Override public void run() {
                    EventSuppression.suppressEvents(new Runnable() {
                        @Override public void run() {
                            enterRemoteCombat(encounter);
                        }
                    });
                }
            });
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
        }
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
                BaseMod.logger.info("SyncExecutor native event opened: " + iface.eventInstanceId);
                return;
            }
        }
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
