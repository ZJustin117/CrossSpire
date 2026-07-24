package crossspire.map;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

/** MapHost capture retries and authoritative topology re-apply after vanilla map generation. */
public final class MapPatches {

    private MapPatches() {}

    @SpirePatch(clz = AbstractDungeon.class, method = "update", paramtypez = {})
    public static class RegisterGeneratedMap {
        @SpirePostfixPatch
        public static void Postfix() {
            if (AbstractDungeon.map != null && !AbstractDungeon.map.isEmpty()) {
                MapHostRegistrationCoordinator.registerPendingElections();
            }
        }
    }

    /**
     * Clients that bootstrap a local run after receiving map_registered still generate a private STS
     * map. Re-apply the accepted authoritative topology immediately after vanilla generateMap.
     */
    @SpirePatch(clz = AbstractDungeon.class, method = "generateMap", paramtypez = {})
    public static class ReapplyAuthoritativeMap {
        @SpirePostfixPatch
        public static void Postfix() {
            MapDefinition definition = StsMapDefinitionApplier.active();
            if (definition == null) return;
            if (StsMapDefinitionApplier.apply(definition)) {
                BaseMod.logger.info("MapPatches re-applied authoritative map after generateMap map="
                    + definition.mapInstanceId);
            } else {
                BaseMod.logger.error("MapPatches failed to re-apply authoritative map after generateMap map="
                    + definition.mapInstanceId);
            }
        }
    }
}
