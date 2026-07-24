package crossspire.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import crossspire.CrossSpireMod;
import crossspire.combat.ComponentAttachmentRegistry;

public final class FullSnapshotSender {

    private FullSnapshotSender() {}

    public static String build() {
        JsonObject snap = FullSnapshotBuilder.buildDirectory(
            CrossSpireMod.partyManager != null ? CrossSpireMod.partyManager.snapshot() : null,
            CrossSpireMod.roomHost != null ? CrossSpireMod.roomHost.getMapRegistry() : null,
            ComponentAttachmentRegistry.all());
        snap.addProperty("source", CrossSpireMod.playerId);
        snap.addProperty("seq", CrossSpireMod.nextSeq());

        JsonObject stage = new JsonObject();
        stage.addProperty("act", AbstractDungeon.actNum);
        stage.addProperty("floor", AbstractDungeon.floorNum);
        snap.add("stage", stage);

        JsonObject player = new JsonObject();
        if (AbstractDungeon.player != null) {
            player.addProperty("hp", AbstractDungeon.player.currentHealth);
            player.addProperty("max_hp", AbstractDungeon.player.maxHealth);
            player.addProperty("block", AbstractDungeon.player.currentBlock);
            player.addProperty("energy", AbstractDungeon.player.energy != null
                ? AbstractDungeon.player.energy.energy : 0);
            player.addProperty("gold", AbstractDungeon.player.gold);
            player.addProperty("character_class", AbstractDungeon.player.getClass().getSimpleName());

            JsonArray powers = new JsonArray();
            JsonArray powerAmounts = new JsonArray();
            for (com.megacrit.cardcrawl.powers.AbstractPower p : AbstractDungeon.player.powers) {
                powers.add(p.ID);
                powerAmounts.add(p.amount);
            }
            player.add("powers", powers);
            player.add("power_amounts", powerAmounts);
        }
        snap.add("player", player);

        JsonArray monsters = new JsonArray();
        if (AbstractDungeon.getCurrRoom() != null && AbstractDungeon.getCurrRoom().monsters != null) {
            for (com.megacrit.cardcrawl.monsters.AbstractMonster m
                : AbstractDungeon.getCurrRoom().monsters.monsters) {
                JsonObject mo = new JsonObject();
                mo.addProperty("id", m.id);
                mo.addProperty("hp", m.currentHealth);
                mo.addProperty("max_hp", m.maxHealth);
                mo.addProperty("block", m.currentBlock);
                mo.addProperty("intent", m.intent != null ? m.intent.name() : "");
                monsters.add(mo);
            }
        }
        snap.add("monsters", monsters);

        return crossspire.network.Protocol.GSON.toJson(snap);
    }
}
