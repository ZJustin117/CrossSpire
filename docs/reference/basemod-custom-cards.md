# BaseMod Custom Cards Documentation

## CustomCard Constructor

`CustomCard(String id, String name, String img, int cost, String rawDescription, CardType type, CardColor color, CardRarity rarity, CardTarget target)`

- `id` - the card id
- `name` - the name of the card
- `img` - the path to the img for this card (image path starts at the root of your `jar`) (250px x 190px); the path to your larger version of the img (500 x 380p) should be img + "_p"
- `cost` - the energy cost of the card
- `rawDescription` - the description for the card
- `type` - the card type, e.g. `ATTACK`, `SKILL`, `POWER`
- `color` - the color of the card; base game options are `RED`, `GREEN`, `COLORLESS`, `CURSE`, `STATUS`
- `rarity` - the card rarity, e.g. `COMMON`, `UNCOMMON`, `RARE`
- `target` - the type of target for the card, e.g. `ENEMY`, `ALL_ENEMIES`, `SELF`, etc...

## Custom Per-Card Textures

### Card Background
`setBackgroundTexture(String smallTexturePath, String largeTexturePath)`
- `smallTexturePath` - the path to the texture (512 x 512p)
- `largeTexturePath` - the path to the inspect view texture (1024 x 1024p)

### Card Energy Orb
`setOrbTexture(String smallTexturePath, String largeTexturePath)`
- `smallTexturePath` - the path to the orb texture (512 x 512p)
- `largeTexturePath` - the path to the inspect view orb texture (164 x 164p)

### Card Rarity Banner
`setBannerTexture(String smallTexturePath, String largeTexturePath)`
- `smallTexturePath` - the path to the texture (512 x 512p)
- `largeTexturePath` - the path to the inspect view texture (1024 x 1024p)

## Registering

Implement `EditCardsSubscriber` in your main mod file and override the `receiveEditCards` method.

- `BaseMod.addCard(AbstractCard card)`
- `BaseMod.removeCard(AbstractCard card)` - removes a card from the game

## Example

```java
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.VulnerablePower;

import basemod.abstracts.CustomCard;

public class Flare extends CustomCard {
    public static final String ID = "myModID:Flare";
    private static CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings(ID);
    public static final String NAME = cardStrings.NAME;
    public static final String DESCRIPTION = cardStrings.DESCRIPTION;
    public static final String IMG_PATH = "img/my_card_img.png";
    private static final int COST = 0;
    private static final int ATTACK_DMG = 3;
    private static final int UPGRADE_PLUS_DMG = 3;
    private static final int VULNERABLE_AMT = 1;
    private static final int UPGRADE_PLUS_VULNERABLE = 1;

    public Flare() {
        super(ID, NAME, IMG_PATH, COST, DESCRIPTION,
            AbstractCard.CardType.ATTACK, AbstractCard.CardColor.RED,
            AbstractCard.CardRarity.UNCOMMON, AbstractCard.CardTarget.ENEMY);
        this.magicNumber = this.baseMagicNumber = VULNERABLE_AMT;
        this.damage = this.baseDamage = ATTACK_DMG;
    }

    @Override
    public void use(AbstractPlayer p, AbstractMonster m) {
        AbstractDungeon.actionManager.addToBottom(new DamageAction(m,
            new DamageInfo(p, this.damage, this.damageTypeForTurn),
            AbstractGameAction.AttackEffect.SLASH_DIAGONAL));
        AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(m, p,
            new VulnerablePower(m, this.magicNumber, false), this.magicNumber,
            true, AbstractGameAction.AttackEffect.NONE));
    }

    @Override
    public AbstractCard makeCopy() {
        return new Flare();
    }

    @Override
    public void upgrade() {
        if (!this.upgraded) {
            this.upgradeName();
            this.upgradeDamage(UPGRADE_PLUS_DMG);
            this.upgradeMagicNumber(UPGRADE_PLUS_VULNERABLE);
        }
    }
}
```

## Note about Inspect View

In the Card Library screen in the Compendium there is an inspect view that brings up a larger version of your card. This is found automatically based off of the original image location by adding a `_p` to the name. This art should have size 500px x 380px.
