package crossspire.sync;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass;
import com.megacrit.cardcrawl.characters.CharacterManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.rooms.RestRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;
import crossspire.network.Protocol;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.remote.RemotePlayerState;

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
        final String seed = "220644";
        BaseMod.logger.info("SyncExecutor room_enter from " + source.substring(0,8) + " monsters=" + ids);

        CombatSyncPatches.suppressBroadcast = true;

        Gdx.app.postRunnable(new Runnable() {
            @Override public void run() {
                EventSuppression.suppressEvents(() -> {
                    createGameIfNeeded(seed);
                    enterRemoteCombat(firstMonster);
                });
            }
        });
    }

    private static void createGameIfNeeded(String seed) {
        if (AbstractDungeon.player != null && CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY) {
            BaseMod.logger.info("SyncExecutor game already running, skip create");
            return;
        }

        try {
            PlayerClass pc = PlayerClass.valueOf("IRONCLAD");
            CharacterManager mgr = new CharacterManager();
            AbstractPlayer player = mgr.setChosenCharacter(pc);
            if (player == null) return;

            if (seed != null && !seed.isEmpty()) SeedHelper.setSeed(seed);

            AbstractDungeon.player = player;
            CrossSpireMod.localPlayer = player;
            AbstractDungeon.generateSeeds();

            MapRoomNode node = new MapRoomNode(0, 0);
            node.room = new RestRoom();
            AbstractDungeon.setCurrMapNode(node);

            CardCrawlGame.mode = CardCrawlGame.GameMode.GAMEPLAY;
            BaseMod.logger.info("SyncExecutor created game state (no Exordium)");
        } catch (Exception e) {
            BaseMod.logger.error("SyncExecutor createGameIfNeeded: " + e.getMessage());
        }
    }

    private static void enterRemoteCombat(String monsterName) {
        if (AbstractDungeon.getCurrRoom() instanceof MonsterRoom) {
            BaseMod.logger.info("SyncExecutor already in combat, skip");
            return;
        }

        String key = resolveEncounterKey(monsterName);
        BaseMod.logger.info("SyncExecutor entering combat: " + monsterName + " key=" + key);

        try {
            MonsterGroup group = MonsterHelper.getEncounter(key);
            MonsterRoom room = new MonsterRoom();
            room.monsters = group;

            AbstractDungeon.getCurrMapNode().room = room;
            room.onPlayerEntry();
            AbstractDungeon.nextRoom = null;
            AbstractDungeon.screen = com.megacrit.cardcrawl.dungeons.AbstractDungeon.CurrentScreen.NONE;

            BaseMod.logger.info("SyncExecutor combat entered: " + monsterName);
        } catch (Exception e) {
            BaseMod.logger.error("SyncExecutor enterRemoteCombat: " + e.getMessage());
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
}
