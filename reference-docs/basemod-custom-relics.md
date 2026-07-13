# BaseMod Custom Relics Documentation

## API

Use `EditRelicsSubscriber`:

- `addRelic(AbstractRelic relic, RelicType type)` - add custom relics to either the shared pool or the pool for a vanilla character
- `addRelicToCustomPool(AbstractRelic relic, CardColor color)` - add a relic to the relic pool for a custom character
- `removeRelic(AbstractRelic relic)` - for vanilla or shared relics, in the `receivePostInitialize` callback
- `removeRelicFromCustomPool(AbstractRelic relic, CardColor color)` - for a modded character's relics, inside `receiveEditRelics`

## RelicStrings

You are **REQUIRED** to set up RelicStrings (see: Custom Strings) otherwise your relic(s) will crash the game during startup.

## CustomRelic Class

The `CustomRelic` class can be extended to make relics easier. It handles loading the texture for you.

Constructor: `CustomRelic(String id, Texture texture, RelicTier tier, LandingSound sfx)`

The size of the relic texture should be `128x128px` with the relic image itself only occupying the center ~48 to 64px box. The rest of the texture should be completely transparent.

## Example

```java
public class Blueberries extends CustomRelic {
    public static final String ID = "Blueberries";
    private static final int HP_PER_CARD = 1;

    public Blueberries() {
        super(ID, MyMod.getBlueberriesTexture(),
            RelicTier.UNCOMMON, LandingSound.MAGICAL);
    }

    @Override
    public String getUpdatedDescription() {
        return DESCRIPTIONS[0] + HP_PER_CARD + DESCRIPTIONS[1];
    }

    @Override
    public void onEquip() {
        int count = 0;
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (c.isEthereal) {
                count++;
            }
        }
        AbstractDungeon.player.increaseMaxHp(count * HP_PER_CARD, true);
    }

    @Override
    public AbstractRelic makeCopy() {
        return new Blueberries();
    }
}
```
