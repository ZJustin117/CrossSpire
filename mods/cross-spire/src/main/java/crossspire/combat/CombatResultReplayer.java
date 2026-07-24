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

    private static final int MAX_INDUCED_HOP = 3;

    public void handleCombatResult(String rawMessage) {
        if (AbstractDungeon.player == null) {
            BaseMod.logger.info("CombatResultReplayer skipped: not in game");
            return;
        }
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String executorId = resolveExecutorId(msg);

        if (executorId.equals(CrossSpireMod.playerId)) {
            BaseMod.logger.info("CombatResultReplayer skip: own result (REAL mode already executed)");
            return;
        }

        JsonArray effects = msg.has("effects") ? msg.getAsJsonArray("effects") : new JsonArray();
        JsonArray opSeq = msg.has("operation_sequence") ? msg.getAsJsonArray("operation_sequence") : new JsonArray();

        BaseMod.logger.info("CombatResultReplayer INDUCED: executor="
            + (executorId.length() >= 8 ? executorId.substring(0, 8) : executorId)
            + " ops=" + opSeq.size() + " eff=" + effects.size());

        // 1) AUTHORITATIVE_APPLY — write numbers under suppression, no passive re-fire
        authoritativeApply(effects);

        // 2) LOCAL_OWNER_ONLY — fire only logic_owner_id == self components
        if (opSeq.size() > 0) {
            localOwnerReplay(opSeq, effects);
        }
    }

    private static String resolveExecutorId(JsonObject msg) {
        if (msg.has("executor_id") && !msg.get("executor_id").isJsonNull()) {
            String id = msg.get("executor_id").getAsString();
            if (id != null && !id.isEmpty()) return id;
        }
        return msg.has("source") ? msg.get("source").getAsString() : "";
    }

    private void authoritativeApply(JsonArray effects) {
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

    private void localOwnerReplay(JsonArray opSeq, JsonArray effects) {
        EffectCapture.startCapture();

        for (JsonElement el : opSeq) {
            JsonObject op = el.getAsJsonObject();
            String step = op.has("step") ? op.get("step").getAsString() : "";

            switch (step) {
                case "play_card":
                    fireLocalOwnerCardTriggers(op);
                    break;
                case "apply_power":
                    applyPowerProjectionOnly(op);
                    break;
                case "vfx":
                    replaySingleVfx(op);
                    break;
                default:
                    break;
            }
        }

        // Also fire local-owner triggers keyed by apply_power effects
        for (JsonElement el : effects) {
            JsonObject eff = el.getAsJsonObject();
            if (!"apply_power".equals(eff.has("kind") ? eff.get("kind").getAsString() : "")) continue;
            String logicOwner = eff.has("logic_owner_id") ? eff.get("logic_owner_id").getAsString() : "";
            if (!LocalOwnerGate.isLocalOwner(logicOwner)) continue;
            String powerId = eff.has("power_id") ? eff.get("power_id").getAsString() : "";
            if (powerId.isEmpty()) continue;
            for (crossspire.reference.Reference<?> ref
                    : crossspire.reference.TriggerRegistry.getTriggers("onPowerApply", powerId)) {
                if (!LocalOwnerGate.mayFirePassive(logicOwner, ref)) continue;
                try { ref.dereference(); } catch (Exception ignored) {}
            }
        }

        Protocol.EffectDescription[] newEffects = EffectCapture.stopCapture();
        submitInducedEffects(newEffects);
    }

    /**
     * LOCAL_OWNER_ONLY card fact: do NOT call BaseMod.publishOnCardUse (ungated full hook replay).
     * Only dereference TriggerRegistry entries owned by self.
     */
    private void fireLocalOwnerCardTriggers(JsonObject op) {
        String cardId = op.has("card_id") ? op.get("card_id").getAsString() : "";
        if (cardId.isEmpty()) return;

        for (crossspire.reference.Reference<?> ref
                : crossspire.reference.TriggerRegistry.getTriggers("onCardUse", cardId)) {
            if (!LocalOwnerGate.isLocalOwner(ref)) continue;
            try {
                ref.dereference();
                BaseMod.logger.info("CombatResultReplayer LOCAL_OWNER onCardUse: " + cardId
                    + " owner=" + ref.ownerId.substring(0, Math.min(8, ref.ownerId.length())));
            } catch (Exception e) {
                BaseMod.logger.info("CombatResultReplayer local trigger failed (" + cardId + "): "
                    + e.getClass().getName() + ": " + e.getMessage());
            }
        }

        for (crossspire.reference.Reference<?> ref
                : crossspire.reference.TriggerRegistry.getTriggers("onCardUse", "*")) {
            if (!LocalOwnerGate.isLocalOwner(ref)) continue;
            try { ref.dereference(); } catch (Exception ignored) {}
        }
    }

    /** Projection-only power apply for non-owners; local owners may run registered triggers. */
    private void applyPowerProjectionOnly(JsonObject op) {
        String powerId = op.has("power_id") ? op.get("power_id").getAsString() : "";
        int amount = op.has("amount") ? op.get("amount").getAsInt() : 0;
        String logicOwner = op.has("logic_owner_id") ? op.get("logic_owner_id").getAsString() : "";
        if (powerId.isEmpty()) return;

        if (!LocalOwnerGate.isLocalOwner(logicOwner)) {
            BaseMod.logger.info("CombatResultReplayer skip non-owner power logic: " + powerId
                + " logic_owner=" + (logicOwner.isEmpty() ? "?" : logicOwner.substring(0, Math.min(8, logicOwner.length()))));
            return;
        }

        for (crossspire.reference.Reference<?> ref
                : crossspire.reference.TriggerRegistry.getTriggers("onPowerApply", powerId)) {
            if (!LocalOwnerGate.mayFirePassive(logicOwner, ref)) continue;
            try { ref.dereference(); } catch (Exception ignored) {}
        }
    }

    private void submitInducedEffects(Protocol.EffectDescription[] newEffects) {
        if (newEffects == null || newEffects.length == 0 || !CrossSpireMod.isConnected()) return;

        for (Protocol.EffectDescription e : newEffects) {
            if (e.hopCount >= MAX_INDUCED_HOP) {
                BaseMod.logger.info("CombatResultReplayer drop induced effect hop=" + e.hopCount);
                return;
            }
            e.originOwnerId = CrossSpireMod.playerId;
            e.hopCount = e.hopCount + 1;
            if (e.logicOwnerId == null || e.logicOwnerId.isEmpty()) {
                e.logicOwnerId = CrossSpireMod.playerId;
            }
        }

        BaseMod.logger.info("CombatResultReplayer submitting " + newEffects.length + " local-owner induced effects");
        Protocol.CombatResultPayload payload = new Protocol.CombatResultPayload();
        payload.executorId = CrossSpireMod.playerId;
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
                    com.megacrit.cardcrawl.characters.AbstractPlayer src = getPlayer();
                    if (m != null && src != null) {
                        m.damage(new DamageInfo(src, amount, DamageInfo.DamageType.NORMAL));
                    } else {
                        BaseMod.logger.info("CombatResultReplayer damage skipped: m="
                            + (m != null) + " player=" + (src != null) + " target=" + target);
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
                    String logicOwner = eff.has("logic_owner_id") ? eff.get("logic_owner_id").getAsString() : "";
                    if (!powerId.isEmpty()) {
                        com.megacrit.cardcrawl.core.AbstractCreature powerTarget =
                            resolvePowerTarget(target);
                        com.megacrit.cardcrawl.characters.AbstractPlayer src = getPlayer();
                        if (powerTarget == null || src == null
                                || AbstractDungeon.actionManager == null) {
                            BaseMod.logger.info("CombatResultReplayer apply_power skipped: target="
                                + target + " power=" + powerId);
                            break;
                        }
                        String hostEntityId = ComponentAttachmentRegistry.hostEntityIdForTarget(target);
                        ComponentAttachmentRegistry.registerApplyPower(
                            powerId, logicOwner, hostEntityId, amount);
                        AbstractDungeon.actionManager.addToBottom(
                            new ApplyPowerAction(
                                powerTarget,
                                src,
                                resolvePower(powerId, amount, logicOwner)));
                        BaseMod.logger.info("CombatResultReplayer apply_power: " + powerId
                            + "→" + target + " x" + amount
                            + " logic_owner=" + (logicOwner.isEmpty() ? "?" : logicOwner.substring(0, Math.min(8, logicOwner.length()))));
                    }
                    break;
                }
                case "remove_power": {
                    String powerId = eff.has("power_id") ? eff.get("power_id").getAsString() : "";
                    if (!powerId.isEmpty()) {
                        ComponentAttachmentRegistry.removePower(
                            powerId, ComponentAttachmentRegistry.hostEntityIdForTarget(target));
                        if ("self".equals(target) && AbstractDungeon.player != null) {
                            AbstractDungeon.player.powers.removeIf(p -> powerId.equals(p.ID));
                        }
                    }
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
                case "remove_relic": {
                    String relicId = eff.has("relic_id") ? eff.get("relic_id").getAsString() : "";
                    if (!relicId.isEmpty() && AbstractDungeon.player != null) {
                        AbstractDungeon.player.loseRelic(relicId);
                    }
                    break;
                }
                case "remove_potion": {
                    String potionId = eff.has("potion_id") ? eff.get("potion_id").getAsString() : "";
                    if (!potionId.isEmpty() && AbstractDungeon.player != null
                        && AbstractDungeon.player.potions != null) {
                        for (int i = AbstractDungeon.player.potions.size() - 1; i >= 0; i--) {
                            com.megacrit.cardcrawl.potions.AbstractPotion p =
                                AbstractDungeon.player.potions.get(i);
                            if (p != null && potionId.equals(p.ID)) {
                                AbstractDungeon.player.removePotion(p);
                                break;
                            }
                        }
                    }
                    break;
                }
                case "obtain_card": {
                    String cardId = eff.has("card_id") ? eff.get("card_id").getAsString() : "";
                    if (!cardId.isEmpty() && AbstractDungeon.player != null
                        && AbstractDungeon.player.masterDeck != null) {
                        AbstractCard card = com.megacrit.cardcrawl.helpers.CardLibrary.getCard(cardId);
                        if (card != null) {
                            AbstractDungeon.player.masterDeck.addToTop(card.makeCopy());
                        }
                    }
                    break;
                }
                case "remove_card": {
                    String cardId = eff.has("card_id") ? eff.get("card_id").getAsString() : "";
                    if (!cardId.isEmpty() && AbstractDungeon.player != null
                        && AbstractDungeon.player.masterDeck != null
                        && AbstractDungeon.player.masterDeck.group != null) {
                        for (int i = AbstractDungeon.player.masterDeck.group.size() - 1; i >= 0; i--) {
                            AbstractCard c = AbstractDungeon.player.masterDeck.group.get(i);
                            if (c != null && cardId.equals(c.cardID)) {
                                AbstractDungeon.player.masterDeck.group.remove(i);
                                break;
                            }
                        }
                    }
                    break;
                }
                case "max_hp":
                    if ("self".equals(target) && AbstractDungeon.player != null) {
                        AbstractDungeon.player.increaseMaxHp(amount, true);
                    }
                    break;
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
        if (AbstractDungeon.getCurrRoom() == null
                || AbstractDungeon.getCurrRoom().monsters == null
                || AbstractDungeon.getCurrRoom().monsters.monsters == null) {
            return null;
        }
        for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
            if (m == null) continue;
            if (targetId != null && (targetId.equals(m.id) || targetId.equals(m.name))) {
                return m;
            }
        }
        // Fallback: first living monster when target is a bare encounter id mismatch
        for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
            if (m != null && !m.isDeadOrEscaped()) return m;
        }
        return null;
    }

    private com.megacrit.cardcrawl.characters.AbstractPlayer getPlayer() {
        if (AbstractDungeon.player != null) return AbstractDungeon.player;
        return CrossSpireMod.localPlayer;
    }

    private void applyPower(String powerId, String target, int amount) {
        applyPower(powerId, target, amount, "");
    }

    private void applyPower(String powerId, String target, int amount, String logicOwnerId) {
        ComponentAttachmentRegistry.registerApplyPower(
            powerId, logicOwnerId, ComponentAttachmentRegistry.hostEntityIdForTarget(target), amount);
        AbstractDungeon.actionManager.addToBottom(
            new ApplyPowerAction(resolvePowerTarget(target), getPlayer(), resolvePower(powerId, amount, logicOwnerId)));
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
        return resolvePower(powerId, amount, null);
    }

    private AbstractPower resolvePower(String powerId, int amount, String logicOwnerId) {
        String className = powerId.endsWith("Power") ? powerId : powerId + "Power";
        com.megacrit.cardcrawl.core.AbstractCreature owner = getPlayer();
        try {
            Class<?> cls = Class.forName("com.megacrit.cardcrawl.powers." + className);
            try {
                return (AbstractPower) cls
                    .getConstructor(com.megacrit.cardcrawl.core.AbstractCreature.class, int.class)
                    .newInstance(owner, amount);
            } catch (NoSuchMethodException e2) {
                // VulnerablePower / WeakPower: (owner, amount, isSourceMonster)
                return (AbstractPower) cls
                    .getConstructor(com.megacrit.cardcrawl.core.AbstractCreature.class, int.class, boolean.class)
                    .newInstance(owner, amount, false);
            }
        } catch (Exception e) {
            BaseMod.logger.info("CombatResultReplayer resolvePower fallback to PowerStub: " + powerId
                + " logic_owner=" + (logicOwnerId == null ? "?" : logicOwnerId));
            return new PowerStub(powerId, amount, logicOwnerId);
        }
    }
}
