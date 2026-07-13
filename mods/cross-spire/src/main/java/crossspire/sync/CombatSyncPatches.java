package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CombatSyncPatches {

    private static final Map<String, List<String>> pendingMonsterIds = new ConcurrentHashMap<String, List<String>>();
    private static final Map<String, List<Integer>> pendingMonsterHps = new ConcurrentHashMap<String, List<Integer>>();
    private static String lastHostSource = null;
    private static boolean justAppliedPending = false;

    public static void storePendingRoom(String source, JsonArray ids, JsonArray hps) {
        List<String> idList = new ArrayList<String>();
        List<Integer> hpList = new ArrayList<Integer>();
        for (int i = 0; i < ids.size(); i++) {
            idList.add(ids.get(i).getAsString());
            hpList.add(i < hps.size() ? hps.get(i).getAsInt() : 50);
        }
        pendingMonsterIds.put(source, idList);
        pendingMonsterHps.put(source, hpList);
        lastHostSource = source;
        BaseMod.logger.info("CombatSync stored pending room: " + idList + " from " + source.substring(0, 8));
    }

    public static void applyPending(MonsterRoom room) {
        if (lastHostSource == null) return;
        List<String> ids = pendingMonsterIds.remove(lastHostSource);
        List<Integer> hps = pendingMonsterHps.remove(lastHostSource);
        if (ids == null || ids.isEmpty()) return;

        BaseMod.logger.info("CombatSync applying pending room from " + lastHostSource.substring(0, 8) + ": " + ids);

        try {
            ArrayList<AbstractMonster> all = new ArrayList<AbstractMonster>();
            for (int i = 0; i < ids.size(); i++) {
                MonsterGroup group = MonsterHelper.getEncounter(ids.get(i));
                if (group != null) {
                    for (AbstractMonster m : group.monsters) {
                        if (i < hps.size()) {
                            m.maxHealth = hps.get(i);
                            m.currentHealth = m.maxHealth;
                        }
                        all.add(m);
                    }
                }
            }
            if (!all.isEmpty()) {
                room.monsters = new MonsterGroup(all.toArray(new AbstractMonster[0]));
                room.monsters.init();
                justAppliedPending = true;
                BaseMod.logger.info("CombatSync applied pending room: " + all.size() + " monsters");
            }
        } catch (Exception e) {
            BaseMod.logger.error("CombatSync applyPending failed: " + e.getMessage());
        }
    }

    @SpirePatch(clz = MonsterRoom.class, method = "onPlayerEntry")
    public static class OnMonsterRoomEntry {
        @SpirePrefixPatch
        public static void Prefix(MonsterRoom __instance) {
            if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;
            applyPending(__instance);
        }

        @SpirePostfixPatch
        public static void Postfix(MonsterRoom __instance) {
            if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;
            if (__instance.monsters == null) return;

            // Don't re-broadcast if we just applied pending monsters from host
            if (justAppliedPending) {
                justAppliedPending = false;
                BaseMod.logger.info("CombatSync skipped host re-broadcast (applied pending)");
                return;
            }

            JsonObject sync = new JsonObject();
            sync.addProperty("type", "stage_sync");
            sync.addProperty("subtype", "room_enter");
            sync.addProperty("source", CrossSpireMod.playerId);
            sync.addProperty("seq", 1);
            sync.addProperty("room_type", "monster");

            JsonArray monsterIds = new JsonArray();
            JsonArray monsterHps = new JsonArray();
            for (AbstractMonster m : __instance.monsters.monsters) {
                monsterIds.add(m.id);
                monsterHps.add(m.maxHealth);
            }
            sync.add("monster_ids", monsterIds);
            sync.add("monster_hps", monsterHps);

            CrossSpireMod.relayClient.send(sync.toString());
            BaseMod.logger.info("CombatSync host broadcast room_enter: " + monsterIds);
        }
    }
}
