package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.rng.SyncedRng;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class MonsterIntentBroadcastPatches {

    private static final List<Protocol.MonsterIntentEntry> intentBuffer = new ArrayList<>();

    @SpirePatch(clz = AbstractMonster.class, method = "createIntent", paramtypez = {})
    public static class OnCreateIntent {
        @SpirePostfixPatch
        public static void postfix(AbstractMonster __instance) {
            if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
            if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;
            if (__instance.isDeadOrEscaped()) return;

            SyncedRng rng = CrossSpireMod.rngManager != null
                ? CrossSpireMod.rngManager.get("monster_intent_" + __instance.id)
                : null;

            int hits = 1;
            String targetId = "self";
            if (__instance.getIntentBaseDmg() > 0 && AbstractDungeon.player != null) {
                if (rng != null) {
                    int playerCount = 1 + RemotePlayerRegistry.count();
                    targetId = rng.nextInt(playerCount) == 0 ? "self" : "player_remote";
                }
            }

            Protocol.MonsterIntentEntry entry = new Protocol.MonsterIntentEntry();
            entry.monsterId = __instance.id;
            entry.intent = __instance.intent.name();
            entry.damage = __instance.getIntentBaseDmg();
            entry.hits = hits;
            entry.targetId = targetId;
            intentBuffer.add(entry);
        }
    }

    @SpirePatch(clz = AbstractPlayer.class, method = "applyStartOfTurnPowers", paramtypez = {})
    public static class FlushSnapshot {
        @SpirePostfixPatch
        public static void postfix(AbstractPlayer __instance) {
            if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
            if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;

            synchronized (intentBuffer) {
                if (intentBuffer.isEmpty()) return;

                Protocol.MonsterIntentMessage msg = new Protocol.MonsterIntentMessage();
                msg.source = CrossSpireMod.playerId;
                msg.seq = CrossSpireMod.nextSeq();
                msg.intents = intentBuffer.toArray(new Protocol.MonsterIntentEntry[0]);

                CrossSpireMod.relayClient.send(Protocol.GSON.toJson(msg));
                BaseMod.logger.info("MonsterIntentBroadcast snapshot: " + intentBuffer.size() + " monsters");
                intentBuffer.clear();
            }
        }
    }
}
