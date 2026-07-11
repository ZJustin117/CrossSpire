package crossspire.sync;

import basemod.BaseMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;
import crossspire.network.Protocol;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.remote.RemotePlayerState;

public class SyncExecutor {

    public void handleSync(String subtype, String source, int seq, String rawMessage) {
        if ("remote_player".equals(subtype)) {
            handleRemotePlayerSync(rawMessage);
        } else if ("combat_result".equals(subtype)) {
            handleCombatResult(rawMessage);
        } else if ("monster_intent".equals(subtype)) {
            BaseMod.logger.info("SyncExecutor monster_intent source=" + source + " seq=" + seq);
        }
    }

    private void handleRemotePlayerSync(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        if (!msg.has("player")) return;

        JsonObject p = msg.getAsJsonObject("player");
        RemotePlayerState rp = RemotePlayerRegistry.get(source);
        if (rp == null) {
            RemotePlayerRegistry.register(source);
            rp = RemotePlayerRegistry.get(source);
        }
        if (rp == null) return;

        if (p.has("hp")) rp.hp = p.get("hp").getAsInt();
        if (p.has("max_hp")) rp.maxHp = p.get("max_hp").getAsInt();
        if (p.has("block")) rp.block = p.get("block").getAsInt();
        if (p.has("energy")) rp.energy = p.get("energy").getAsInt();

        BaseMod.logger.info("SyncExecutor remote_player " + source.substring(0, 8) + " hp=" + rp.hp + " blk=" + rp.block);
    }

    private void handleCombatResult(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String cardId = msg.has("card_id") ? msg.get("card_id").getAsString() : "";
        JsonArray effects = msg.has("effects") ? msg.getAsJsonArray("effects") : new JsonArray();

        AbstractCard localCard = CardLibrary.getCard(cardId);
        if (localCard != null) {
            replayWithCard(cardId, msg);
        } else {
            fallbackEffects(effects, cardId);
        }
    }

    private void replayWithCard(String cardId, JsonObject msg) {
        BaseMod.logger.info("SyncExecutor replay_with_card: " + cardId);
        EventSuppression.suppressEvents(() -> {
            AbstractCard card = CardLibrary.getCard(cardId);
            if (card == null) return;

            AbstractDungeon.player.hand.addToTop(card.makeCopy());
            BaseMod.logger.info("SyncExecutor replay: added " + cardId + " to hand, suppressing events");
        });
    }

    private void fallbackEffects(JsonArray effects, String sourceCard) {
        BaseMod.logger.info("SyncExecutor fallback_effects: " + sourceCard + " effects=" + effects.size());
        EventSuppression.suppressEvents(() -> {
            for (JsonElement el : effects) {
                JsonObject eff = el.getAsJsonObject();
                String kind = eff.has("kind") ? eff.get("kind").getAsString() : "";
                String target = eff.has("target") ? eff.get("target").getAsString() : "";
                int amount = eff.has("amount") ? eff.get("amount").getAsInt() : 0;

                BaseMod.logger.info("SyncExecutor fallback: " + kind + "→" + target + " x" + amount);
                applyGenericEffect(kind, target, amount);
            }
        });
    }

    private void applyGenericEffect(String kind, String target, int amount) {
        switch (kind) {
            case "damage": {
                AbstractMonster m = findMonster(target);
                if (m != null) {
                    m.currentHealth -= amount;
                    BaseMod.logger.info("SyncExecutor damage " + target + " for " + amount + " (hp=" + m.currentHealth + ")");
                }
                break;
            }
            case "gain_block": {
                if ("self".equals(target) && AbstractDungeon.player != null) {
                    AbstractDungeon.player.addBlock(amount);
                }
                break;
            }
            case "heal":
                if ("self".equals(target) && AbstractDungeon.player != null) {
                    AbstractDungeon.player.heal(amount, true);
                }
                break;
            case "gain_energy":
                if ("self".equals(target) && AbstractDungeon.player != null) {
                    AbstractDungeon.player.gainEnergy(amount);
                }
                break;
            case "lose_hp":
                if ("self".equals(target) && AbstractDungeon.player != null) {
                    AbstractDungeon.player.currentHealth -= amount;
                }
                break;
            default:
                BaseMod.logger.info("SyncExecutor unhandled effect: " + kind);
                break;
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
