package crossspire.sync;

import basemod.BaseMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

        BaseMod.logger.info("SyncExecutor battle_start from " + (source.length() >= 8 ? source.substring(0, 8) : source) + " char=" + charName + " seed=" + seed);
        CrossSpireMod.pendingStartSeed = seed;
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
        if (p.has("character_class")) rp.characterClass = p.get("character_class").getAsString();

        if (p.has("powers") && p.has("power_amounts")) {
            JsonArray pw = p.getAsJsonArray("powers");
            JsonArray pa = p.getAsJsonArray("power_amounts");
            int len = Math.min(pw.size(), pa.size());
            rp.powers = new String[len];
            rp.powerAmounts = new int[len];
            for (int i = 0; i < len; i++) {
                rp.powers[i] = pw.get(i).getAsString();
                rp.powerAmounts[i] = pa.get(i).getAsInt();
            }
        }

        if (source.length() >= 8) {
            BaseMod.logger.info("SyncExecutor remote_player " + source.substring(0, 8) + " hp=" + rp.hp + " blk=" + rp.block);
        }
    }

    private void handleRoomEnter(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        if (source.equals(CrossSpireMod.playerId)) return;

        JsonArray ids = msg.has("monster_ids") ? msg.getAsJsonArray("monster_ids") : new JsonArray();
        BaseMod.logger.info("SyncExecutor room_enter from " + (source.length() >= 8 ? source.substring(0, 8) : source) + " monsters=" + ids);
    }
}
