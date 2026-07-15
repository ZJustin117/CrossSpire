package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.ui.buttons.EndTurnButton;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import crossspire.ui.QueueDisplay;

@SpirePatch(clz = EndTurnButton.class, method = "enable", paramtypez = {})
public class EndTurnSyncPatches {

    public static boolean suppressEndTurn = false;

    @SpirePrefixPatch
    public static SpireReturn<Void> prefix(EndTurnButton __instance) {
        if (!CrossSpireMod.isConnected()) return SpireReturn.Continue();
        if (!QueueDisplay.isEndTurnAllowed() && QueueDisplay.size() > 0) {
            return SpireReturn.Return(null);
        }
        return SpireReturn.Continue();
    }

    @SpirePostfixPatch
    public static void postfix(EndTurnButton __instance) {
        if (suppressEndTurn) return;
        if (!CrossSpireMod.isConnected()) return;

        Protocol.PlayerEndTurnMessage msg = new Protocol.PlayerEndTurnMessage();
        msg.source = CrossSpireMod.playerId;
        msg.seq = CrossSpireMod.nextSeq();
        CrossSpireMod.send(Protocol.GSON.toJson(msg));
        BaseMod.logger.info("EndTurnSync broadcast player_end_turn");
    }
}
