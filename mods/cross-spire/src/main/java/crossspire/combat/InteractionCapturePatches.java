package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import crossspire.CrossSpireMod;
import crossspire.network.InteractMessageSender;

@SuppressWarnings("unused")
public class InteractionCapturePatches {

    @SpirePatch(clz = GridCardSelectScreen.class, method = "open",
            paramtypez = {CardGroup.class, int.class, String.class, boolean.class})
    public static class OnGridCardSelect {
        @SpirePrefixPatch
        public static void Prefix(GridCardSelectScreen __instance,
                CardGroup cardPool, int selectAmount, String tipMsg, boolean anyNumber) {
            if (!CrossSpireMod.isConnected()) return;
            if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
            if (cardPool == null || cardPool.group == null) return;

            String[] options = new String[cardPool.group.size()];
            for (int i = 0; i < cardPool.group.size(); i++) {
                options[i] = cardPool.group.get(i).cardID;
            }

            String msg = InteractMessageSender.buildInteractRequest(
                CrossSpireMod.playerId, "card_select", options,
                tipMsg != null ? tipMsg : "Select a card", selectAmount, anyNumber ? 99 : selectAmount);

            if (CrossSpireMod.isRoomHost()) {
                CrossSpireMod.messageRouter.handleInteractRequest(msg);
            } else {
                CrossSpireMod.send((String) msg);
            }
            BaseMod.logger.info("InteractionCapturePatches gridCardSelect: cards="
                + options.length + " select=" + selectAmount);
        }
    }
}
