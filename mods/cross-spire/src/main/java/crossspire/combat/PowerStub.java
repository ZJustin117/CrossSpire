package crossspire.combat;

import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.stances.AbstractStance;

/**
 * Display/projection power stub. Non-logic-owner callbacks are no-ops so
 * remote-owned buffs never fire on this node (ARCHITECTURE §8).
 */
public class PowerStub extends AbstractPower {

    public final String logicOwnerId;
    private final PowerLogicGate gate;

    public PowerStub(String powerId, int amount) {
        this(powerId, amount, null);
    }

    public PowerStub(String powerId, int amount, String logicOwnerId) {
        this.ID = powerId;
        this.name = powerId;
        this.amount = amount;
        this.type = PowerType.BUFF;
        this.logicOwnerId = logicOwnerId;
        this.gate = new PowerLogicGate(logicOwnerId);
    }

    public boolean isLocalLogicOwner() {
        return gate.isLocalLogicOwner();
    }

    /** True if any gated callback was suppressed due to non-local ownership. */
    public boolean wasBlockedByOwnership() {
        return gate.wasBlockedByOwnership();
    }

    @Override
    public void updateDescription() {
        this.description = "";
    }

    @Override
    public void atStartOfTurn() {
        if (!gate.allowLogic()) return;
        super.atStartOfTurn();
    }

    @Override
    public void duringTurn() {
        if (!gate.allowLogic()) return;
        super.duringTurn();
    }

    @Override
    public void atStartOfTurnPostDraw() {
        if (!gate.allowLogic()) return;
        super.atStartOfTurnPostDraw();
    }

    @Override
    public void atEndOfTurn(boolean isPlayer) {
        if (!gate.allowLogic()) return;
        super.atEndOfTurn(isPlayer);
    }

    @Override
    public void atEndOfTurnPreEndTurnCards(boolean isPlayer) {
        if (!gate.allowLogic()) return;
        super.atEndOfTurnPreEndTurnCards(isPlayer);
    }

    @Override
    public void atEndOfRound() {
        if (!gate.allowLogic()) return;
        super.atEndOfRound();
    }

    @Override
    public void onSpecificTrigger() {
        if (!gate.allowLogic()) return;
        super.onSpecificTrigger();
    }

    @Override
    public void onDeath() {
        if (!gate.allowLogic()) return;
        super.onDeath();
    }

    @Override
    public void onEnergyRecharge() {
        if (!gate.allowLogic()) return;
        super.onEnergyRecharge();
    }

    @Override
    public void onRemove() {
        if (!gate.allowLogic()) return;
        super.onRemove();
    }

    @Override
    public void onInitialApplication() {
        if (!gate.allowLogic()) return;
        super.onInitialApplication();
    }

    @Override
    public void stackPower(int stackAmount) {
        // Stacking is display state; always allow so AUTHORITATIVE_APPLY can update amount.
        super.stackPower(stackAmount);
    }

    @Override
    public void onAttack(DamageInfo info, int damageAmount, AbstractCreature target) {
        if (!gate.allowLogic()) return;
        super.onAttack(info, damageAmount, target);
    }

    @Override
    public int onAttacked(DamageInfo info, int damageAmount) {
        if (!gate.allowLogic()) return damageAmount;
        return super.onAttacked(info, damageAmount);
    }

    @Override
    public void onCardDraw(AbstractCard card) {
        if (!gate.allowLogic()) return;
        super.onCardDraw(card);
    }

    @Override
    public void onPlayCard(AbstractCard card, AbstractMonster m) {
        if (!gate.allowLogic()) return;
        super.onPlayCard(card, m);
    }

    @Override
    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (!gate.allowLogic()) return;
        super.onUseCard(card, action);
    }

    @Override
    public void onAfterUseCard(AbstractCard card, UseCardAction action) {
        if (!gate.allowLogic()) return;
        super.onAfterUseCard(card, action);
    }

    @Override
    public void onExhaust(AbstractCard card) {
        if (!gate.allowLogic()) return;
        super.onExhaust(card);
    }

    @Override
    public void onAfterCardPlayed(AbstractCard card) {
        if (!gate.allowLogic()) return;
        super.onAfterCardPlayed(card);
    }

    @Override
    public void onDamageAllEnemies(int[] damage) {
        if (!gate.allowLogic()) return;
        super.onDamageAllEnemies(damage);
    }

    @Override
    public int onHeal(int healAmount) {
        if (!gate.allowLogic()) return healAmount;
        return super.onHeal(healAmount);
    }

    @Override
    public void wasHPLost(DamageInfo info, int damageAmount) {
        if (!gate.allowLogic()) return;
        super.wasHPLost(info, damageAmount);
    }

    @Override
    public void onEvokeOrb(AbstractOrb orb) {
        if (!gate.allowLogic()) return;
        super.onEvokeOrb(orb);
    }

    @Override
    public void onChannel(AbstractOrb orb) {
        if (!gate.allowLogic()) return;
        super.onChannel(orb);
    }

    @Override
    public void onChangeStance(AbstractStance oldStance, AbstractStance newStance) {
        if (!gate.allowLogic()) return;
        super.onChangeStance(oldStance, newStance);
    }

    @Override
    public void onGainedBlock(float blockAmount) {
        if (!gate.allowLogic()) return;
        super.onGainedBlock(blockAmount);
    }

    @Override
    public float atDamageGive(float damage, DamageInfo.DamageType type) {
        if (!gate.isLocalLogicOwner()) return damage;
        return super.atDamageGive(damage, type);
    }

    @Override
    public float atDamageReceive(float damage, DamageInfo.DamageType type) {
        if (!gate.isLocalLogicOwner()) return damage;
        return super.atDamageReceive(damage, type);
    }

    @Override
    public float modifyBlock(float blockAmount) {
        if (!gate.isLocalLogicOwner()) return blockAmount;
        return super.modifyBlock(blockAmount);
    }
}
