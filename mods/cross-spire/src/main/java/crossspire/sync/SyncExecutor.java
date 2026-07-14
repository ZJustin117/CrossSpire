package crossspire.sync;

import basemod.BaseMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import crossspire.CrossSpireMod;
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
        BaseMod.logger.info("SyncExecutor monster_intent: " + msg.monsterId + " intent=" + msg.intent + " dmg=" + msg.damage + " hits=" + msg.hits);
        IntentRenderer.show(msg.monsterId, msg.intent, msg.damage, msg.hits);
    }

    public void handleCombatResult(String rawMessage) {
        Protocol.CombatResultMessage msg = Protocol.GSON.fromJson(rawMessage, Protocol.CombatResultMessage.class);
        BaseMod.logger.info("SyncExecutor combat_result: " + msg.monsterId + " effects=" + (msg.effects != null ? msg.effects.length : 0));
        CrossSpireMod.messageRouter.replayCombatResult(msg.effects);
    }

    public void handleEventResult(String rawMessage) {
        Protocol.EventResultMessage msg = Protocol.GSON.fromJson(rawMessage, Protocol.EventResultMessage.class);
        BaseMod.logger.info("SyncExecutor event_result: " + msg.eventId + " effects=" + (msg.effects != null ? msg.effects.length : 0));
        if (msg.effects != null && msg.effects.length > 0) {
            CrossSpireMod.messageRouter.replayCombatResult(msg.effects);
        }
    }

    private void handleBattleStart(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String charName = msg.has("character") ? msg.get("character").getAsString() : "IRONCLAD";
        String seed = msg.has("seed") ? msg.get("seed").getAsString() : "";
        String source = msg.has("source") ? msg.get("source").getAsString() : "";

        if (!source.isEmpty() && CrossSpireMod.stageHost != null) {
            CrossSpireMod.stageHost.setStageHost(source);
            BaseMod.logger.info("SyncExecutor stage_host set to: " + source.substring(0, 8));
        }

        if (source.equals(CrossSpireMod.playerId)) return;
        BaseMod.logger.info("SyncExecutor battle_start from " + (source.length() >= 8 ? source.substring(0,8) : source));
        com.megacrit.cardcrawl.helpers.SeedHelper.setSeed(seed);
        CrossSpireMod.syncedSeed = seed;
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

        String firstMonster = ids.get(0).getAsString();
        BaseMod.logger.info("SyncExecutor room_enter from " + source.substring(0, 8) + " monsters=" + ids);

        CombatSyncPatches.suppressBroadcast = true;

        final String monster = firstMonster;
        new Thread(new Runnable() {
            @Override public void run() {
                for (int attempt = 0; attempt < 20; attempt++) {
                    try { Thread.sleep(3000); } catch (InterruptedException e) { break; }
                    if (AbstractDungeon.player == null || CardCrawlGame.mode != CardCrawlGame.GameMode.GAMEPLAY) {
                        BaseMod.logger.info("SyncExecutor room_enter starting remote game (attempt " + attempt + ")");
                        writeBatch("crossspire start IRONCLAD " +
                            (CrossSpireMod.syncedSeed != null ? CrossSpireMod.syncedSeed : "220644"));
                        try { Thread.sleep(5000); } catch (InterruptedException e2) { break; }
                        continue;
                    }
                    if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.MonsterRoom) {
                        BaseMod.logger.info("SyncExecutor room_enter already in combat, skipping fight");
                        return;
                    }
                    BaseMod.logger.info("SyncExecutor entering remote combat (attempt " + attempt + "): " + monster);
                    writeBatch("fight " + monster);
                    BaseMod.logger.info("SyncExecutor entered remote combat via fight: " + monster);
                    return;
                }
                BaseMod.logger.info("SyncExecutor room_enter giving up after 20 attempts");
            }
        }, "RoomEnterDeferred").start();
    }

    private static void writeBatch(String command) {
        try {
            java.io.File f = new java.io.File("/storage/emulated/0/Android/data/io.stamethyst/files/sts/crossspire_batch.txt");
            java.io.FileWriter fw = new java.io.FileWriter(f);
            fw.write(command + "\n");
            fw.close();
        } catch (Exception ignored) {}
    }
}
