# BaseMod Custom Colors Documentation

## Description

To create a new color for a custom character, you must patch the `AbstractCard.CardColor` enum using `ModTheSpire`'s enum patching feature.

**You MUST define both a CardColor and LibraryType value and they MUST have identical names.**

## Enums

```java
import com.evacipated.cardcrawl.modthespire.lib.SpireEnum;
import com.megacrit.cardcrawl.cards.AbstractCard;

public class AbstractCardEnum {
    @SpireEnum
    public static AbstractCard.CardColor MOD_NAME_COLOR;
}
```

```java
import com.evacipated.cardcrawl.modthespire.lib.SpireEnum;
import com.megacrit.cardcrawl.helpers.CardLibrary;

public class LibraryTypeEnum {
    @SpireEnum
    public static CardLibrary.LibraryType MOD_NAME_COLOR;
}
```

## How to Use

Register the custom color with BaseMod in your `@SpireInitializer` method. Do NOT call it in `postInitialize` or any other hooks.

## API

### Full-parameter version:

`addColor(AbstractCard.CardColor color, Color bgColor, Color backColor, Color frameColor, Color frameOutlineColor, Color descBoxColor, Color trailVfxColor, Color glowColor, String attackBg, String skillBg, String powerBg, String energyOrb, String attackBgPortrait, String skillBgPortrait, String powerBgPortrait, String energyOrbPortrait, String cardEnergyOrb)`

- `color` - should be `MY_CUSTOM_COLOR`
- `bgColor` - background color
- `backColor` - back color
- `frameColor` - frame color
- `frameOutlineColor` - frame outline color
- `descBoxColor` - the description box color
- `trailVfxColor` - Visual effects trail color
- `glowColor` - glow color
- `attackBg` - path to your attack background image
- `skillBg` - path to your skill background image
- `powerBg` - path to your power background image
- `energyOrb` - path to your energy orb image
- `attackBgPortrait` - path to your attack background image for the card inspect view
- `skillBgPortrait` - path to your skill background image for the card inspect view
- `powerBgPortrait` - path to your power background image for the card inspect view
- `energyOrbPortrait` - path to your energy orb image for the card inspect view
- `cardEnergyOrb` - path to your small energy orb image for card descriptions

### Simplified version:

`addColor(AbstractCard.CardColor color, Color everythingColor, String attackBg, String skillBg, String powerBg, String energyOrb, String attackBgPortrait, String skillBgPortrait, String powerBgPortrait, String energyOrbPortrait, String cardEnergyOrb)`

- `everythingColor` - use the same color for all the color parameters
