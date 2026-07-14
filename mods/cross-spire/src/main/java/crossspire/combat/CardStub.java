package crossspire.combat;

import com.megacrit.cardcrawl.cards.AbstractCard;

public class CardStub extends AbstractCard {

    public CardStub(String cardId, int cost, CardType type, CardRarity rarity, CardTarget target) {
        super(cardId, "Stub", "status/beta", cost, "",
                type, CardColor.COLORLESS, rarity, target);
    }

    @Override
    public void use(com.megacrit.cardcrawl.characters.AbstractPlayer p,
                    com.megacrit.cardcrawl.monsters.AbstractMonster m) {
    }

    @Override
    public void upgrade() {
    }

    @Override
    public AbstractCard makeCopy() {
        return new CardStub(cardID, cost, type, rarity, target);
    }
}
