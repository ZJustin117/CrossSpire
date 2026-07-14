package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;
import crossspire.network.Protocol;

public class GoldSyncPatches {

    @SpirePatch(clz = AbstractPlayer.class, method = "gainGold", paramtypez = {int.class}, optional = true)
    public static class GainGold {
        @SpirePostfixPatch
        public static void postfix(AbstractPlayer __instance, int amount) {
            if (EventSuppression.isSuppressed()) return;
            broadcastGold("gain_gold", amount, __instance);
        }
    }

    @SpirePatch(clz = AbstractPlayer.class, method = "loseGold", paramtypez = {int.class}, optional = true)
    public static class LoseGold {
        @SpirePostfixPatch
        public static void postfix(AbstractPlayer __instance, int amount) {
            if (EventSuppression.isSuppressed()) return;
            broadcastGold("gain_gold", -amount, __instance);
        }
    }

    private static void broadcastGold(String kind, int amount, AbstractPlayer player) {
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;

        Protocol.PlayerStateMessage msg = new Protocol.PlayerStateMessage();
        msg.source = CrossSpireMod.playerId;
        msg.seq = (int) (System.currentTimeMillis() % 100000);
        msg.player = new Protocol.RemotePlayerState();
        msg.player.hp = player.currentHealth;
        msg.player.maxHp = player.maxHealth;
        msg.player.block = player.currentBlock;
        msg.player.gold = player.gold;
        msg.player.energy = AbstractDungeon.player != null ? AbstractDungeon.player.energy.energy : 0;
        msg.player.characterClass = player.getClass().getSimpleName();

        CrossSpireMod.relayClient.send(Protocol.GSON.toJson(msg));
        BaseMod.logger.info("GoldSyncPatches " + kind + ": " + amount);
    }
}
