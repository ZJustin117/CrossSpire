package crossspire.combat;

import basemod.BaseMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import crossspire.EventSuppression;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;

public class CombatResultReplayer {

    public void handleCombatResult(String rawMessage) {
        if (AbstractDungeon.player == null) {
            BaseMod.logger.info("CombatResultReplayer skipped: not in game");
            return;
        }
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String cardId = msg.has("card_id") ? msg.get("card_id").getAsString() : "";
        JsonArray effects = msg.has("effects") ? msg.getAsJsonArray("effects") : new JsonArray();
        JsonArray opSeq = msg.has("operation_sequence") ? msg.getAsJsonArray("operation_sequence") : new JsonArray();

        AbstractCard localCard = CardLibrary.getCard(cardId);
        if (localCard != null && opSeq.size() > 0) {
            replayVfx(opSeq);
            replayWithCard(cardId, opSeq, effects);
        } else {
            replayVfx(opSeq);
            fallbackEffects(effects, cardId);
        }
    }

    public void applyEffects(Protocol.EffectDescription[] effects) {
        if (AbstractDungeon.player == null) {
            BaseMod.logger.info("CombatResultReplayer applyEffects skipped: not in game");
            return;
        }
        StringBuilder fx = new StringBuilder();
        for (int i = 0; i < effects.length; i++) {
            if (i > 0) fx.append(", ");
            fx.append(effects[i].kind).append("=").append(effects[i].amount);
        }
        BaseMod.logger.info("CombatResultReplayer applyEffects: " + effects.length + " effects: " + fx);
        EventSuppression.suppressEvents(() -> {
            for (Protocol.EffectDescription eff : effects) {
                JsonObject fakeEl = new JsonObject();
                fakeEl.addProperty("kind", eff.kind);
                fakeEl.addProperty("target", eff.target);
                fakeEl.addProperty("amount", eff.amount);
                if (eff.powerId != null) fakeEl.addProperty("power_id", eff.powerId);
                if (eff.cardId != null) fakeEl.addProperty("card_id", eff.cardId);
                if (eff.relicId != null) fakeEl.addProperty("relic_id", eff.relicId);
                if (eff.potionId != null) fakeEl.addProperty("potion_id", eff.potionId);
                applyEffect(eff.kind, eff.target, eff.amount, fakeEl);
            }
        });
    }

    private void replayWithCard(String cardId, JsonArray opSeq, JsonArray effects) {
        BaseMod.logger.info("CombatResultReplayer replay: " + cardId + " steps=" + opSeq.size() + " effects=" + effects.size());
        fallbackEffects(effects, cardId);
    }

    private void fallbackEffects(JsonArray effects, String sourceCard) {
        BaseMod.logger.info("CombatResultReplayer fallback: " + sourceCard + " effects=" + effects.size());
        EventSuppression.suppressEvents(() -> {
            for (JsonElement el : effects) {
                JsonObject eff = el.getAsJsonObject();
                String kind = eff.has("kind") ? eff.get("kind").getAsString() : "";
                String target = eff.has("target") ? eff.get("target").getAsString() : "";
                int amount = eff.has("amount") ? eff.get("amount").getAsInt() : 0;

                BaseMod.logger.info("CombatResultReplayer effect: " + kind + "→" + target + " x" + amount);
                applyEffect(kind, target, amount, eff);
            }
        });
    }

    private void applyEffect(String kind, String target, int amount, JsonObject eff) {
        try {
            switch (kind) {
                case "damage": {
                    AbstractMonster m = findMonster(target);
                    if (m != null) { m.currentHealth -= amount; }
                    break;
                }
                case "gain_block":
                    if ("self".equals(target) && AbstractDungeon.player != null)
                        AbstractDungeon.player.addBlock(amount);
                    break;
                case "heal":
                    if ("self".equals(target) && AbstractDungeon.player != null)
                        AbstractDungeon.player.heal(amount, true);
                    break;
                case "gain_energy":
                    if ("self".equals(target) && AbstractDungeon.player != null)
                        AbstractDungeon.player.gainEnergy(amount);
                    break;
                case "lose_hp":
                    if ("self".equals(target) && AbstractDungeon.player != null)
                        AbstractDungeon.player.currentHealth -= amount;
                    break;
                case "apply_power": {
                    String powerId = eff.has("power_id") ? eff.get("power_id").getAsString() : "";
                    if (!powerId.isEmpty() && getPlayer() != null)
                        applyPower(powerId, target, amount);
                    break;
                }
                case "remove_power": {
                    String powerId = eff.has("power_id") ? eff.get("power_id").getAsString() : "";
                    if (!powerId.isEmpty() && AbstractDungeon.player != null)
                        AbstractDungeon.player.powers.removeIf(p -> powerId.equals(p.ID));
                    break;
                }
                case "draw_card":
                    if ("self".equals(target) && AbstractDungeon.player != null)
                        for (int i = 0; i < amount; i++) AbstractDungeon.player.draw();
                    break;
                case "discard_card": {
                    String cardId = eff.has("card_id") ? eff.get("card_id").getAsString() : "";
                    if ("self".equals(target) && AbstractDungeon.player != null)
                        discardCard(cardId, amount);
                    break;
                }
                case "exhaust_card": {
                    String cardId = eff.has("card_id") ? eff.get("card_id").getAsString() : "";
                    if ("self".equals(target) && AbstractDungeon.player != null)
                        exhaustCard(cardId);
                    break;
                }
                case "gain_gold":
                    if ("self".equals(target) && AbstractDungeon.player != null)
                        AbstractDungeon.player.gainGold(amount);
                    break;
                case "obtain_relic": {
                    String relicId = eff.has("relic_id") ? eff.get("relic_id").getAsString() : "";
                    if (!relicId.isEmpty() && AbstractDungeon.player != null) {
                        AbstractRelic relic = com.megacrit.cardcrawl.helpers.RelicLibrary.getRelic(relicId);
                        if (relic != null) relic.instantObtain();
                    }
                    break;
                }
                case "obtain_potion": {
                    String potionId = eff.has("potion_id") ? eff.get("potion_id").getAsString() : "";
                    if (!potionId.isEmpty() && getPlayer() != null) {
                        com.megacrit.cardcrawl.potions.AbstractPotion p = com.megacrit.cardcrawl.helpers.PotionHelper.getPotion(potionId);
                        if (p != null) {
                            getPlayer().obtainPotion(p);
                            BaseMod.logger.info("CombatResultReplayer obtained potion: " + potionId);
                        }
                    }
                    break;
                }
                default:
                    BaseMod.logger.info("CombatResultReplayer unhandled: " + kind);
                    break;
            }
        } catch (Exception e) {
            BaseMod.logger.error("CombatResultReplayer effect error (" + kind + "): " + e.getMessage());
        }
    }

    private void discardCard(String cardId, int count) {
        int discarded = 0;
        for (int i = AbstractDungeon.player.hand.size() - 1; i >= 0 && discarded < Math.max(1, count); i--) {
            AbstractCard c = AbstractDungeon.player.hand.group.get(i);
            if (cardId.isEmpty() || c.cardID.equals(cardId)) {
                AbstractDungeon.player.hand.moveToDiscardPile(c);
                discarded++;
            }
        }
    }

    private void exhaustCard(String cardId) {
        for (int i = AbstractDungeon.player.hand.size() - 1; i >= 0; i--) {
            AbstractCard c = AbstractDungeon.player.hand.group.get(i);
            if (c.cardID.equals(cardId)) {
                AbstractDungeon.player.hand.moveToExhaustPile(c);
                return;
            }
        }
    }

    private AbstractMonster findMonster(String targetId) {
        if (AbstractDungeon.getCurrRoom() == null) return null;
        for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
            if (targetId.equals(m.id)) return m;
        }
        return null;
    }

    private com.megacrit.cardcrawl.characters.AbstractPlayer getPlayer() {
        if (AbstractDungeon.player != null) return AbstractDungeon.player;
        return CrossSpireMod.localPlayer;
    }

    private void applyPower(String powerId, String target, int amount) {
        try {
            String[] tokens = ("power " + powerId + " " + amount).split(" ");
            basemod.devcommands.ConsoleCommand.execute(tokens);
            BaseMod.logger.info("CombatResultReplayer apply_power: " + powerId + "→" + target + " x" + amount);
        } catch (Exception e) {
            BaseMod.logger.error("CombatResultReplayer apply_power failed (" + powerId + "): " + e.getMessage());
        }
    }

    private void replayVfx(JsonArray opSeq) {
        if (AbstractDungeon.effectList == null || opSeq.size() == 0) return;
        for (JsonElement el : opSeq) {
            JsonObject op = el.getAsJsonObject();
            if (!"vfx".equals(op.has("step") ? op.get("step").getAsString() : "")) continue;
            String vfxKind = op.has("vfx_kind") ? op.get("vfx_kind").getAsString() : "";
            float cx = AbstractDungeon.player != null ? AbstractDungeon.player.hb.cX : 0;
            float cy = AbstractDungeon.player != null ? AbstractDungeon.player.hb.cY : 0;

            if ("ATTACK".equals(vfxKind)) {
                AbstractDungeon.effectList.add(new com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect(cx, cy,
                    com.megacrit.cardcrawl.actions.AbstractGameAction.AttackEffect.SLASH_HORIZONTAL, false));
                BaseMod.logger.info("CombatResultReplayer vfx ATTACK");
            }
        }
    }
}
