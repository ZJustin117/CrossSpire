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
import crossspire.network.Protocol;

public class CombatResultReplayer {

    public void handleCombatResult(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String cardId = msg.has("card_id") ? msg.get("card_id").getAsString() : "";
        JsonArray effects = msg.has("effects") ? msg.getAsJsonArray("effects") : new JsonArray();
        JsonArray opSeq = msg.has("operation_sequence") ? msg.getAsJsonArray("operation_sequence") : new JsonArray();

        AbstractCard localCard = CardLibrary.getCard(cardId);
        if (localCard != null && opSeq.size() > 0) {
            replayWithCard(cardId, opSeq);
        } else {
            fallbackEffects(effects, cardId);
        }
    }

    private void replayWithCard(String cardId, JsonArray opSeq) {
        BaseMod.logger.info("CombatResultReplayer replay: " + cardId + " steps=" + opSeq.size());
        EventSuppression.suppressEvents(() -> {
            AbstractCard card = CardLibrary.getCard(cardId);
            if (card == null) return;
            AbstractDungeon.player.hand.addToTop(card.makeCopy());
        });
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
                    BaseMod.logger.info("CombatResultReplayer apply_power: " + eff.get("power_id").getAsString());
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
                    BaseMod.logger.info("CombatResultReplayer obtain_potion: " + eff.get("potion_id").getAsString());
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
}
