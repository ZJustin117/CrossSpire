package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.esotericsoftware.spine.AnimationState;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;

@SuppressWarnings("unused")
public class AnimationSyncPatches {

    @SpirePatch(clz = AnimationState.class, method = "setAnimation",
        paramtypez = {int.class, String.class, boolean.class})
    public static class OnSetAnimation {
        @SpirePostfixPatch
        public static void Postfix(AnimationState __instance, int trackIndex, String animationName, boolean loop) {

            if (!CrossSpireMod.isConnected()) return;
            if (CrossSpireMod.playerId.isEmpty()) return;
            if (trackIndex != 0) return;
            if (animationName == null) return;

            com.megacrit.cardcrawl.characters.AbstractPlayer p =
                com.megacrit.cardcrawl.dungeons.AbstractDungeon.player;
            if (p == null) return;

            com.esotericsoftware.spine.AnimationState playerState;
            try {
                playerState = (com.esotericsoftware.spine.AnimationState)
                    basemod.ReflectionHacks.getPrivate(p,
                        com.megacrit.cardcrawl.core.AbstractCreature.class, "state");
            } catch (Exception e) {
                return;
            }

            if (__instance != playerState) return;

            Protocol.AnimationSyncMessage msg = new Protocol.AnimationSyncMessage();
            msg.source = CrossSpireMod.playerId;
            msg.seq = CrossSpireMod.nextSeq();
            msg.playerId = CrossSpireMod.playerId;
            msg.animationName = animationName;

            CrossSpireMod.send(Protocol.GSON.toJson(msg));
            BaseMod.logger.info("AnimationSync broadcast: " + animationName);
        }
    }
}
