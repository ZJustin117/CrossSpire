package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.ui.buttons.EndTurnButton;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;

@SpirePatch(clz = EndTurnButton.class, method = "enable", paramtypez = {})
public class EndTurnSyncPatches {

    public static boolean suppressEndTurn = false;

    @SpirePostfixPatch
    public static void postfix(EndTurnButton __instance) {
        if (suppressEndTurn) return;
        if (!CrossSpireMod.isConnected()) return;

        Protocol.PlayerEndTurnMessage msg = new Protocol.PlayerEndTurnMessage();
        msg.source = CrossSpireMod.playerId;
        msg.seq = (int) (System.currentTimeMillis() % 100000);
        CrossSpireMod.send(Protocol.GSON.toJson(msg));
        BaseMod.logger.info("EndTurnSync broadcast player_end_turn");
    }
}
