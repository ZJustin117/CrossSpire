package crossspire.combat;

import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.core.AbstractCreature;

public class PowerStub extends AbstractPower {

    public PowerStub(String powerId, int amount) {
        this.ID = powerId;
        this.name = powerId;
        this.amount = amount;
        this.type = PowerType.BUFF;
    }

    @Override
    public void updateDescription() {
        this.description = "";
    }
}
