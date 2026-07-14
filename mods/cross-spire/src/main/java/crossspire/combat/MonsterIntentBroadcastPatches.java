package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.rng.SyncedRng;

@SpirePatch(clz = AbstractMonster.class, method = "createIntent", paramtypez = {})
public class MonsterIntentBroadcastPatches {

    @SpirePostfixPatch
    public static void postfix(AbstractMonster __instance) {
        if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;

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

        Protocol.MonsterIntentMessage msg = new Protocol.MonsterIntentMessage();
        msg.source = CrossSpireMod.playerId;
        msg.seq = CrossSpireMod.nextSeq();
        msg.monsterId = __instance.id;
        msg.intent = __instance.intent.name();
        msg.damage = __instance.getIntentBaseDmg();
        msg.hits = hits;
        msg.targetId = targetId;

        CrossSpireMod.relayClient.send(Protocol.GSON.toJson(msg));
        BaseMod.logger.info("MonsterIntentBroadcast: " + __instance.id + " intent=" + msg.intent + " target=" + targetId);
    }
}
