package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;

@SpirePatch(clz = AbstractMonster.class, method = "createIntent", paramtypez = {})
public class MonsterIntentBroadcastPatches {

    @SpirePostfixPatch
    public static void postfix(AbstractMonster __instance) {
        if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;

        Protocol.MonsterIntentMessage msg = new Protocol.MonsterIntentMessage();
        msg.source = CrossSpireMod.playerId;
        msg.seq = (int) (System.currentTimeMillis() % 100000);
        msg.monsterId = __instance.id;
        msg.intent = __instance.intent.name();
        msg.damage = __instance.getIntentBaseDmg();
        msg.hits = 1;
        msg.targetId = "self";

        CrossSpireMod.relayClient.send(Protocol.GSON.toJson(msg));
        BaseMod.logger.info("MonsterIntentBroadcast: " + __instance.id + " intent=" + msg.intent);
    }
}
