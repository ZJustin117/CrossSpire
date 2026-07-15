package crossspire.combat;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class CardStub extends AbstractCard {

    private final String stubCardId;

    public CardStub(String cardId, int cost, CardType type, CardRarity rarity, CardTarget target) {
        super(cardId, "Stub", "status/beta", cost, "", type, CardColor.COLORLESS, rarity, target);
        this.stubCardId = cardId;
        this.freeToPlayOnce = true;
        this.exhaust = true;
        this.exhaustOnUseOnce = true;
    }

    @Override
    public void use(AbstractPlayer p, AbstractMonster m) {
    }

    @Override
    public void upgrade() {
    }

    @Override
    public void calculateCardDamage(AbstractMonster m) {
        this.damage = 0;
        this.block = 0;
        this.magicNumber = 0;
        this.isDamageModified = false;
        this.isBlockModified = false;
    }

    @Override
    public void applyPowers() {
        this.damage = 0;
        this.block = 0;
        this.isDamageModified = false;
    }

    @Override
    public AbstractCard makeCopy() {
        return new CardStub(stubCardId, cost, type, rarity, target);
    }
}
