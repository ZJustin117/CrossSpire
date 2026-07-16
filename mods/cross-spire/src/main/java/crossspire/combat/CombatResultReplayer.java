package crossspire.combat;

import basemod.BaseMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import crossspire.EventSuppression;
import crossspire.CrossSpireMod;
import crossspire.network.PacketOperation;
import crossspire.network.Protocol;
import crossspire.network.StandardPacket;

public class CombatResultReplayer {

    public void handleCombatResult(String rawMessage) {
        if (AbstractDungeon.player == null) {
            BaseMod.logger.info("CombatResultReplayer skipped: not in game");
            return;
        }
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String senderId = msg.has("source") ? msg.get("source").getAsString() : "";

        if (senderId.equals(CrossSpireMod.playerId)) {
            BaseMod.logger.info("CombatResultReplayer skip: own result (REAL mode already executed)");
            return;
        }

        JsonArray effects = msg.has("effects") ? msg.getAsJsonArray("effects") : new JsonArray();
        JsonArray opSeq = msg.has("operation_sequence") ? msg.getAsJsonArray("operation_sequence") : new JsonArray();

        BaseMod.logger.info("CombatResultReplayer INDUCED mode: sender=" + senderId.substring(0, 8)
            + " ops=" + opSeq.size() + " eff=" + effects.size());

        if (opSeq.size() > 0) {
            replayInduced(opSeq, effects);
        } else {
            fallbackEffects(effects, "unknown");
        }
    }

    private void replayInduced(JsonArray opSeq, JsonArray effects) {
        EffectCapture.startCapture();

        for (JsonElement el : opSeq) {
            JsonObject op = el.getAsJsonObject();
            String step = op.has("step") ? op.get("step").getAsString() : "";

            switch (step) {
                case "play_card":
                    inducedUseCard(op);
                    break;
                case "apply_power":
                    publishPostPowerApply(op);
                    break;
                case "vfx":
                    replaySingleVfx(op);
                    break;
                default:
                    break;
            }
        }

        Protocol.EffectDescription[] newEffects = EffectCapture.stopCapture();

        EventSuppression.suppressEvents(() -> {
            for (JsonElement el : effects) {
                JsonObject eff = el.getAsJsonObject();
                String kind = eff.has("kind") ? eff.get("kind").getAsString() : "";
                String target = eff.has("target") ? eff.get("target").getAsString() : "";
                int amount = eff.has("amount") ? eff.get("amount").getAsInt() : 0;
                applyEffect(kind, target, amount, eff);
            }
        });

        if (newEffects.length > 0 && CrossSpireMod.isConnected()) {
            BaseMod.logger.info("CombatResultReplayer submitting " + newEffects.length + " new induced effects");
            Protocol.CombatResultPayload payload = new Protocol.CombatResultPayload();
            payload.effects = newEffects;
            payload.operationSequence = new Protocol.OperationStep[0];

            StandardPacket pkt = new StandardPacket();
            pkt.packetId = CrossSpireMod.playerId + "-" + CrossSpireMod.nextSeq();
            pkt.source = CrossSpireMod.playerId;
            pkt.seq = CrossSpireMod.nextSeq();
            pkt.timestamp = System.currentTimeMillis();
            pkt.refId = "combat:induced@" + CrossSpireMod.playerId;
            pkt.ownerId = CrossSpireMod.playerId;
            pkt.operation = PacketOperation.COMBAT_RESULT;
            pkt.payload = Protocol.GSON.toJsonTree(payload).getAsJsonObject();

            CrossSpireMod.send(StandardPacket.toJson(pkt));
        }
    }

    private void inducedUseCard(JsonObject op) {
        String cardId = op.has("card_id") ? op.get("card_id").getAsString() : "";
        String targetId = op.has("target") ? op.get("target").getAsString() : "self";
        if (cardId.isEmpty()) return;

        String cardType = op.has("card_type") ? op.get("card_type").getAsString() : "ATTACK";
        String cardRarity = op.has("card_rarity") ? op.get("card_rarity").getAsString() : "BASIC";
        String cardTarget = op.has("card_target") ? op.get("card_target").getAsString() : "ENEMY";

        AbstractCard stubCard = new CardStub(cardId, 1,
            AbstractCard.CardType.valueOf(cardType),
            AbstractCard.CardRarity.valueOf(cardRarity),
            AbstractCard.CardTarget.valueOf(cardTarget));

        try {
            BaseMod.publishOnCardUse(stubCard);
            BaseMod.logger.info("CombatResultReplayer INDUCED publishOnCardUse: " + cardId);

            for (crossspire.reference.Reference<?> ref : crossspire.reference.TriggerRegistry.getTriggers("onCardUse", cardId)) {
                try { ref.dereference(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            BaseMod.logger.info("CombatResultReplayer publishOnCardUse failed (" + cardId + "): " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private void publishPostPowerApply(JsonObject op) {
        String powerId = op.has("power_id") ? op.get("power_id").getAsString() : "";
        int amount = op.has("amount") ? op.get("amount").getAsInt() : 0;
        if (powerId.isEmpty()) return;

        AbstractPower power = resolvePower(powerId, amount);
        if (power == null) return;

        try {
            BaseMod.publishPostPowerApply(power, AbstractDungeon.player, AbstractDungeon.player);
            BaseMod.logger.info("CombatResultReplayer published postPowerApply: " + powerId + " x" + amount);
        } catch (Exception e) {
            BaseMod.logger.info("CombatResultReplayer postPowerApply failed (" + powerId + "): " + e.getMessage());
        }
    }

    private void replaySingleVfx(JsonObject op) {
        String vfxKind = op.has("vfx_kind") ? op.get("vfx_kind").getAsString() : "";
        String target = op.has("target") ? op.get("target").getAsString() : "self";

        if (AbstractDungeon.effectList == null) return;

        if ("ATTACK".equals(vfxKind)) {
            AbstractMonster m = findMonster(target);
            float cx = m != null ? m.hb.cX : AbstractDungeon.player.hb.cX;
            float cy = m != null ? m.hb.cY : AbstractDungeon.player.hb.cY;
            AbstractDungeon.effectList.add(new com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect(cx, cy,
                com.megacrit.cardcrawl.actions.AbstractGameAction.AttackEffect.SLASH_HORIZONTAL, false));
            BaseMod.logger.info("CombatResultReplayer vfx ATTACK on " + target);
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

    private void fallbackEffects(JsonArray effects, String sourceCard) {
        BaseMod.logger.info("CombatResultReplayer fallback: " + sourceCard + " effects=" + effects.size());
        EventSuppression.suppressEvents(() -> {
            for (JsonElement el : effects) {
                JsonObject eff = el.getAsJsonObject();
                String kind = eff.has("kind") ? eff.get("kind").getAsString() : "";
                String target = eff.has("target") ? eff.get("target").getAsString() : "";
                int amount = eff.has("amount") ? eff.get("amount").getAsInt() : 0;
                applyEffect(kind, target, amount, eff);
            }
        });
    }

    private void applyEffect(String kind, String target, int amount, JsonObject eff) {
        try {
            switch (kind) {
                case "damage": {
                    AbstractMonster m = findMonster(target);
                    if (m != null) {
                        m.damage(new DamageInfo(getPlayer(), amount, DamageInfo.DamageType.NORMAL));
                    }
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
                    if (!powerId.isEmpty()) {
                        AbstractDungeon.actionManager.addToBottom(
                            new ApplyPowerAction(resolvePowerTarget(target), getPlayer(), resolvePower(powerId, amount)));
                    }
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
        AbstractDungeon.actionManager.addToBottom(
            new ApplyPowerAction(resolvePowerTarget(target), getPlayer(), resolvePower(powerId, amount)));
        BaseMod.logger.info("CombatResultReplayer apply_power: " + powerId + "→" + target + " x" + amount);
    }

    private com.megacrit.cardcrawl.core.AbstractCreature resolvePowerTarget(String target) {
        if (!"self".equals(target)) {
            AbstractMonster m = findMonster(target);
            if (m != null) return m;
        }
        return getPlayer();
    }

    private AbstractPower resolvePower(String powerId, int amount) {
        try {
            return (AbstractPower) Class.forName("com.megacrit.cardcrawl.powers." + powerId)
                .getConstructor(com.megacrit.cardcrawl.core.AbstractCreature.class, int.class)
                .newInstance(getPlayer(), amount);
        } catch (Exception e) {
            BaseMod.logger.info("CombatResultReplayer resolvePower fallback to PowerStub: " + powerId);
            return new PowerStub(powerId, amount);
        }
    }
}

