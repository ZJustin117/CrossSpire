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
import crossspire.combat.ComponentAttachmentRegistry;

public class CombatSyncPatches {

    public static boolean suppressBroadcast = false;

    @SpirePatch(clz = MonsterRoom.class, method = "onPlayerEntry", paramtypez = {})
    public static class OnMonsterRoomEntry {
        @SpirePostfixPatch
        public static void Postfix(MonsterRoom __instance) {
            ComponentAttachmentRegistry.clear();
            if (suppressBroadcast) {
                suppressBroadcast = false;
                RenderSafetyPatches.remoteCombatActive = true;
                AbstractDungeon.nextRoom = null;
                return;
            }
            if (!CrossSpireMod.isConnected()) return;
            if (__instance.monsters == null) return;
            // Co-op map path: node_instance_opened drives dual enter; do not also room_enter.
            if (crossspire.party.ActiveNodeTracker.getNodeInstanceId() != null
                && !crossspire.party.ActiveNodeTracker.getNodeInstanceId().isEmpty()) {
                BaseMod.logger.info("CombatSync skip room_enter: active node_instance="
                    + crossspire.party.ActiveNodeTracker.getNodeInstanceId());
                RenderSafetyPatches.remoteCombatActive = true;
                return;
            }
            if (CrossSpireMod.partyManager != null && CrossSpireMod.playerId != null) {
                crossspire.party.PartyState party = CrossSpireMod.partyManager.getParty(
                    CrossSpireMod.partyManager.getPartyIdForPlayer(CrossSpireMod.playerId));
                if (party != null && party.mapInstanceId != null && !party.mapInstanceId.isEmpty()) {
                    BaseMod.logger.info("CombatSync skip room_enter: party map bound "
                        + party.mapInstanceId);
                    return;
                }
            }

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
            BaseMod.logger.info("CombatSync broadcast room_enter (legacy host-spawn): " + monsterIds);
        }
    }
}
