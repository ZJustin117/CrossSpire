package crossspire.sync;

import basemod.BaseMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
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
            BaseMod.logger.info("SyncExecutor monster_intent source=" + source + " seq=" + seq);
        }
    }

    private void handleBattleStart(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String charName = msg.has("character") ? msg.get("character").getAsString() : "IRONCLAD";
        String seed = msg.has("seed") ? msg.get("seed").getAsString() : "";
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
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
        JsonArray hps = msg.has("monster_hps") ? msg.getAsJsonArray("monster_hps") : new JsonArray();
        BaseMod.logger.info("SyncExecutor room_enter from " + (source.length() >= 8 ? source.substring(0, 8) : source) + " monsters=" + ids);

        try {
            java.util.ArrayList<AbstractMonster> all = new java.util.ArrayList<AbstractMonster>();
            String[] exordiumPkgs = {"exordium", "city", "beyond", "ending"};
            for (int i = 0; i < ids.size(); i++) {
                String monsterId = ids.get(i).getAsString();
                AbstractMonster m = createMonster(monsterId);
                if (m == null) {
                    BaseMod.logger.error("SyncExecutor room_enter: cannot create " + monsterId);
                    continue;
                }
                if (i < hps.size()) { m.maxHealth = hps.get(i).getAsInt(); m.currentHealth = m.maxHealth; }
                all.add(m);
            }
            if (!all.isEmpty()) {
                CombatSyncPatches.storePendingGroup(new MonsterGroup(all.toArray(new AbstractMonster[0])), source);
                BaseMod.logger.info("SyncExecutor room_enter stored: " + all.size() + " monsters");
            }
        } catch (Exception e) {
            BaseMod.logger.error("SyncExecutor room_enter error: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private AbstractMonster createMonster(String id) {
        try {
            String[] tokens = {"fight", id};
            basemod.devcommands.ConsoleCommand.execute(tokens);
            if (com.megacrit.cardcrawl.dungeons.AbstractDungeon.getCurrRoom() != null
                && com.megacrit.cardcrawl.dungeons.AbstractDungeon.getCurrRoom().monsters != null
                && !com.megacrit.cardcrawl.dungeons.AbstractDungeon.getCurrRoom().monsters.monsters.isEmpty()) {
                return com.megacrit.cardcrawl.dungeons.AbstractDungeon.getCurrRoom().monsters.monsters.get(0);
            }
        } catch (Exception e) {
            BaseMod.logger.error("createMonster error: " + e.getMessage());
        }
        return null;
    }
}
