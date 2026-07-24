package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.screens.DungeonMapScreen;
import crossspire.CrossSpireMod;
import crossspire.party.ActiveNodeTracker;
import crossspire.party.PartyManager;
import crossspire.party.PartyState;

/**
 * T9.3: when the RoomInstanceHost locally opens the dungeon map (continue / leave room),
 * broadcast {@code room_exit_unlocked} so the party can pin. Non-hosts only unlock locally
 * if already unlocked (no forged party unlock).
 */
public final class MapUnlockPatches {

    private static long lastBroadcastMs;
    private static String lastBroadcastRoom = "";

    private MapUnlockPatches() {}

    public static boolean isLocalRoomInstanceHost() {
        if (CrossSpireMod.partyManager == null || CrossSpireMod.playerId == null) return false;
        String partyId = ActiveNodeTracker.getPartyId();
        if (partyId == null || partyId.isEmpty()) {
            partyId = CrossSpireMod.partyManager.getPartyIdForPlayer(CrossSpireMod.playerId);
        }
        if (partyId == null || partyId.isEmpty()) partyId = PartyManager.DEFAULT_PARTY_ID;
        PartyState party = CrossSpireMod.partyManager.getParty(partyId);
        if (party == null || party.nodeInstanceHostId == null || party.nodeInstanceHostId.isEmpty()) {
            return false;
        }
        return party.nodeInstanceHostId.equals(CrossSpireMod.playerId);
    }

    /** Dedup rapid openMap spam from STS UI. */
    public static boolean shouldBroadcastUnlock(String roomInstanceId, long nowMs) {
        if (roomInstanceId == null) roomInstanceId = "";
        if (roomInstanceId.equals(lastBroadcastRoom) && nowMs - lastBroadcastMs < 1500L) {
            return false;
        }
        lastBroadcastRoom = roomInstanceId;
        lastBroadcastMs = nowMs;
        return true;
    }

    public static void tryBroadcastHostMapUnlock(String reason) {
        if (!CrossSpireMod.isConnected()) return;
        if (CrossSpireMod.roomNavigationGate != null
            && CrossSpireMod.roomNavigationGate.isExitUnlocked()
            && !CrossSpireMod.roomNavigationGate.getRoomInstanceId().isEmpty()) {
            return;
        }
        if (!isLocalRoomInstanceHost()) {
            BaseMod.logger.info("MapUnlockPatches skip unlock: not room instance host");
            return;
        }
        String roomId = ActiveNodeTracker.getNodeInstanceId();
        if (roomId == null || roomId.isEmpty()) {
            if (CrossSpireMod.roomNavigationGate != null) {
                roomId = CrossSpireMod.roomNavigationGate.getRoomInstanceId();
            }
        }
        if (roomId == null || roomId.isEmpty()) {
            BaseMod.logger.info("MapUnlockPatches skip unlock: no active room instance");
            return;
        }
        if (!shouldBroadcastUnlock(roomId, System.currentTimeMillis())) {
            return;
        }
        MessageRouter.broadcastRoomExitUnlocked(reason != null ? reason : "map_open");
    }

    @SpirePatch(clz = DungeonMapScreen.class, method = "open", paramtypez = {boolean.class})
    public static class OnDungeonMapOpen {
        @SpirePostfixPatch
        public static void Postfix(DungeonMapScreen __instance, boolean playSound) {
            tryBroadcastHostMapUnlock("dungeon_map_open");
        }
    }

    @SpirePatch(clz = AbstractEvent.class, method = "openMap", paramtypez = {})
    public static class OnEventOpenMap {
        @SpirePostfixPatch
        public static void Postfix(AbstractEvent __instance) {
            tryBroadcastHostMapUnlock("event_open_map");
        }
    }
}
