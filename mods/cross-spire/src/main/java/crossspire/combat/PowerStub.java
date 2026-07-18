package crossspire.combat;

import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.core.AbstractCreature;

public class PowerStub extends AbstractPower {

    public final String logicOwnerId;

    public PowerStub(String powerId, int amount) {
        this(powerId, amount, null);
    }

    public PowerStub(String powerId, int amount, String logicOwnerId) {
        this.ID = powerId;
        this.name = powerId;
        this.amount = amount;
        this.type = PowerType.BUFF;
        this.logicOwnerId = logicOwnerId;
    }

    public boolean isLocalLogicOwner() {
        return LocalOwnerGate.isLocalOwner(logicOwnerId);
    }

    @Override
    public void updateDescription() {
        this.description = "";
    }
}
