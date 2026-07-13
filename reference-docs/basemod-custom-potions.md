# BaseMod Custom Potions Documentation

## API

- `ArrayList<String> getPotionsToRemove()` - list of potions that are being removed
- `void addPotion(Class potionClass, Color liquidColor, Color hybridColor, Color spotsColor, String potionID)` - add a new custom potion to the game
- `void addPotion(Class potionClass, Color liquidColor, Color hybridColor, Color spotsColor, String potionID, AbstractPlayer.PlayerClass playerClass)` - add a new class specific custom potion to the game
- **Not currently working** `void removePotion(String potionID)` - remove a potion from the game

## PotionStrings

You must set up PotionStrings (similar to RelicStrings and CardStrings) in your localization files.
