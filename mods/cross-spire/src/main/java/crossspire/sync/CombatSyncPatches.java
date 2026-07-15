package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import crossspire.CrossSpireMod;

public class CombatSyncPatches {

    public static boolean suppressBroadcast = false;

    @SpirePatch(clz = MonsterRoom.class, method = "onPlayerEntry")
    public static class OnMonsterRoomEntry {
        @SpirePostfixPatch
        public static void Postfix(MonsterRoom __instance) {
            if (suppressBroadcast) {
                suppressBroadcast = false;
                RenderSafetyPatches.remoteCombatActive = true;
                AbstractDungeon.nextRoom = null;
                if (AbstractDungeon.topLevelEffects != null) AbstractDungeon.topLevelEffects.clear();
                if (AbstractDungeon.effectList != null) AbstractDungeon.effectList.clear();
                return;
            }
            if (!CrossSpireMod.isConnected()) return;
            if (__instance.monsters == null) return;

            JsonObject sync = new JsonObject();
            sync.addProperty("type", "stage_sync");
            sync.addProperty("subtype", "room_enter");
            sync.addProperty("source", CrossSpireMod.playerId);
            sync.addProperty("seq", 1);

            JsonArray monsterIds = new JsonArray();
            JsonArray monsterHps = new JsonArray();
            for (AbstractMonster m : __instance.monsters.monsters) {
                monsterIds.add(m.id);
                monsterHps.add(m.maxHealth);
            }
            sync.add("monster_ids", monsterIds);
            sync.add("monster_hps", monsterHps);

            CrossSpireMod.send(sync.toString());
            BaseMod.logger.info("CombatSync broadcast room_enter: " + monsterIds);
        }
    }
}
