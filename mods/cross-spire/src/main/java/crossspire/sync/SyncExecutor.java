package crossspire.sync;

import basemod.BaseMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.remote.RemotePlayerState;
import crossspire.remote.GameStarter;

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

        BaseMod.logger.info("SyncExecutor battle_start from " + source.substring(0, 8) + " char=" + charName + " seed=" + seed);
        try {
            GameStarter.start(charName, seed);
        } catch (Exception e) {
            BaseMod.logger.error("SyncExecutor battle_start failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private void handleRemotePlayerSync(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        if (!msg.has("player")) return;

        JsonObject p = msg.getAsJsonObject("player");
        RemotePlayerState rp = RemotePlayerRegistry.get(source);
        if (rp == null) {
            RemotePlayerRegistry.register(source);
            rp = RemotePlayerRegistry.get(source);
        }
        if (rp == null) return;

        if (p.has("hp")) rp.hp = p.get("hp").getAsInt();
        if (p.has("max_hp")) rp.maxHp = p.get("max_hp").getAsInt();
        if (p.has("block")) rp.block = p.get("block").getAsInt();
        if (p.has("energy")) rp.energy = p.get("energy").getAsInt();

        BaseMod.logger.info("SyncExecutor remote_player " + source.substring(0, 8) + " hp=" + rp.hp + " blk=" + rp.block);
    }

    private void handleRoomEnter(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        if (source.equals(CrossSpireMod.playerId)) return;

        JsonArray ids = msg.has("monster_ids") ? msg.getAsJsonArray("monster_ids") : new JsonArray();
        JsonArray hps = msg.has("monster_hps") ? msg.getAsJsonArray("monster_hps") : new JsonArray();

        BaseMod.logger.info("SyncExecutor room_enter from " + source.substring(0, 8) + " monsters=" + ids);

        try {
            java.util.ArrayList<AbstractMonster> allMonsters = new java.util.ArrayList<AbstractMonster>();
            for (int i = 0; i < ids.size(); i++) {
                String monsterId = ids.get(i).getAsString();
                MonsterGroup group = MonsterHelper.getEncounter(monsterId);
                if (group != null) {
                    for (AbstractMonster m : group.monsters) {
                        if (i < hps.size()) {
                            m.maxHealth = hps.get(i).getAsInt();
                            m.currentHealth = m.maxHealth;
                        }
                        allMonsters.add(m);
                    }
                }
            }

            if (!allMonsters.isEmpty()) {
                MonsterGroup finalGroup = new MonsterGroup(allMonsters.toArray(new AbstractMonster[0]));
                AbstractDungeon.getCurrRoom().monsters = finalGroup;
                finalGroup.init();
                BaseMod.logger.info("SyncExecutor room_enter applied: " + allMonsters.size() + " monsters");
            }
        } catch (Exception e) {
            BaseMod.logger.error("SyncExecutor room_enter error: " + e.getMessage());
        }
    }
}
