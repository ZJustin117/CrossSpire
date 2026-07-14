# BaseMod Hooks Documentation

## Subscription Model

BaseMod uses a subscription model to handle hooks. If you want to receive a specific hook you `subscribeTo` it.

### Subscription Methods

- `BaseMod.subscribe(this)` - subscribes `this` to **all** hooks it implements subscriber for
- `BaseMod.unsubscribe(this)` - unsubscribes `this` from **all** hooks it implements subscriber for
- `BaseMod.subscribe(this, Class<? extends ISubscriber> toAddClass>)` - subscribes `this` to **only** the `toAddClass` hook
- `BaseMod.unsubscribe(this, Class<? extends ISubscriber> toRemoveClass)` - unsubscribes `this` from **only** the `toRemoveClass` hook

### UnsubscribeLater

Use `BaseMod.unsubscribeLater` when you need to unsubscribe from an event after it's triggered once, to avoid `ConcurrentModificationException`.

## Adder Hooks

Here you can add custom content to the game.

| Name | Description |
|------|-------------|
| AddAudio | Register audio here. With `BaseMod.addAudio` |
| AddCustomModeMods | Register CustomMods here. With `customMods.add` |
| EditCards | Register cards here. With `BaseMod.addCard` |
| EditCharacters | Register characters here. With `BaseMod.addCharacter` |
| EditKeywords | Register keywords here. With `BaseMod.addKeyword` |
| EditRelics | Register relics here. With `BaseMod.addRelic` |
| EditStrings | Register string files here. With `BaseMod.loadCustomStringsFile` |
| SetUnlocks | Register unlocks here |
| PostInitialize | The game finished initializing. Called after all Adder-hooks |

## Before (Pre) Hooks

Called before the event.

| Name | Parameter | Return | Description |
|------|-----------|--------|-------------|
| PreStartGame | | | Before starting or continuing a game |
| OnPlayerTurnStart | | | At start of turn. Before `GameActionManager.turn` increment |
| OnPlayerTurnStartPostDraw | | | At start of turn, after draw cards action is queued |

### Pre (modifiable)

Before the event. Event parameter and objects can be modified.

| Name | Parameter | Return | Description |
|------|-----------|--------|-------------|
| PostCampfire | | `boolean` | When using a campfire action. Returning false allows another campfire action |
| MaxHpChange | `int amount` | `int` | The max hp of the player changes. Return value overrides amount |
| OnCardUse | `AbstractCard card` | | A card is used |
| OnCreateDescription | `String rawDescription, AbstractCard card` | `String` | The description for a card is created. Return value overrides description |
| OnPlayerDamaged | `int amount, DamageInfo info` | `int` | The player takes damage. Called before block is considered |
| OnPlayerLoseBlock | `int amount` | `int` | The player loses block at the end of their turn |
| PreMonsterTurn | `AbstractMonster monster` | `boolean` | Before the monster starts its turn. Returning false skips the monster turn |
| PrePotionUse | `AbstractPotion potion` | | A potion is used |

## After (Post) Hooks

Called after the event.

| Name | Parameter | Description |
|------|-----------|-------------|
| OnPowersModified | | A power has been modified |
| PostDeath | | The player died. Also triggers with `Abandon Run` |
| PostDungeonInitialize | | The dungeon initialized |
| PostEnergyRecharge | | The energy got recharged |
| StartAct | | A new act was started |
| StartGame | | When starting or continuing a game |

### Post (modifiable)

| Name | Parameter | Description |
|------|-----------|-------------|
| OnStartBattle | `AbstractRoom room` | A battle started |
| PostBattle | `AbstractRoom room` | A battle ended. Doesn't trigger when losing |
| PostCreateShopPotion | `ArrayList<StorePotion> storePotions, ShopScreen screenInstance` | The shop potions were created |
| PostCreateShopRelic | `ArrayList<StoreRelic> storeRelics, ShopScreen screenInstance` | The shop relics were created |
| PostCreateStartingDeck | `PlayerClass chosenClass, CardGroup cardGroup` | The starting deck was created |
| PostCreateStartingRelics | `PlayerClass chosenClass, ArrayList<String> startingRelics` | The starting relics were created |
| PostDraw | `AbstractCard card` | After a card was drawn |
| PostExhaust | `AbstractCard card` | Card was exhausted |
| PostPotionUse | `AbstractPotion potion` | A potion has been used |
| PostPowerApply | `AbstractPower power, AbstractCreature target, AbstractCreature source` | A power was applied |
| PotionGet | `AbstractPotion potion` | The player got a potion |
| RelicGet | `AbstractRelic relic` | The player got a relic |

## Render Hooks

| Name | Parameter | Description |
|------|-----------|-------------|
| ModelRender | `ModelBatch batch, Environment env` | Before the image is rendered |
| PostRender | `SpriteBatch sb` | After everything rendered. Above everything |
| PreRender | `OrthographicCamera camera` | Before the image is rendered |
| PreRoomRender | `SpriteBatch sb` | Before the room is rendered |
| Render | `SpriteBatch sb` | Right before everything is rendered |

## Update Hooks

| Name | Description |
|------|-------------|
| PostDungeonUpdate | The dungeon was updated |
| PostPlayerUpdate | The player was updated |
| PostUpdate | Everything was updated |
| PreDungeonUpdate | Before the dungeon is updated |
| PrePlayerUpdate | Before the player is updated |
| PreUpdate | Before anything updates |

## Update & Render Order

1. PreRenderSubscriber
2. ModelRenderSubscriber
3. **Input read**
4. PreUpdateSubscriber
5. PreDungeonUpdateSubscriber
6. PrePlayerUpdateSubscriber
7. PostPlayerUpdateSubscriber
8. PostDungeonUpdateSubscriber
9. PostUpdateSubscriber
10. **Input disposed**
11. PreRoomRenderSubscriber
12. RenderSubscriber
13. PostRenderSubscriber

## Adder (Initialization) Order

1. EditStringsSubscriber
2. AddAudioSubscriber
3. EditKeywordsSubscriber
4. SetUnlocksSubscriber
5. EditCardsSubscriber
6. EditRelicsSubscriber
7. PostCreateStartingRelicsSubscriber
8. EditCharactersSubscriber
9. PostInitializeSubscriber
