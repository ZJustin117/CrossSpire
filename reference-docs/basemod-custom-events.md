# BaseMod Custom Events Documentation

## Creating an Event

Create a class that extends `AbstractEvent` (or any abstract class that extends it).

```java
public class MyFirstEvent extends AbstractImageEvent {
    public static final String ID = "My First Event";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString(ID);
    private static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    private static final String[] OPTIONS = eventStrings.OPTIONS;
    private static final String NAME = eventStrings.NAME;

    public MyFirstEvent() {
        super(NAME, DESCRIPTIONS[0], "img/events/eventpicture.png");
        this.imageEventText.setDialogOption(OPTIONS[0]);
    }

    @Override
    protected void buttonEffect(int buttonPressed) {
        // Handle button click - see basegame event classes for examples
    }
}
```

## Adding the Event

Call this method in `receivePostInitialize`:

`BaseMod.addEvent(String eventID, Class<? extends AbstractEvent> class, String dungeonID)`

- `eventID` : The ID of the event
- `class` : The class of the event you are adding
- `dungeonID` : The ID of the dungeon whose pool you want to add the event to (e.g. `Exordium.ID`, `TheCity.ID`, `TheBeyond.ID`). Leaving off this argument adds the event to all pools.

```java
@Override
public void receivePostInitialize() {
    BaseMod.addEvent(MyFirstEvent.ID, MyFirstEvent.class);
    BaseMod.addEvent(MySecondEvent.ID, MySecondEvent.class, TheCity.ID);
}
```

## Custom Event Conditions

`BaseMod.addEvent(AddEventParams params)`

```java
BaseMod.addEvent(new AddEventParams.Builder(MySecondEvent.ID, MySecondEvent.class)
    .dungeonID(TheCity.ID)
    .playerClass(MyPlayerClassEnum.MY_PLAYER_CLASS)
    .spawnCondition(() -> /* condition */)
    .bonusCondition(() -> /* condition */)
    .create());
```

### Builder methods:

- `dungeonID(String dungeonID)` / `dungeonIDs(String... dungeonIDs)` - Limits the event to appearing in the specified acts
- `playerClass(AbstractPlayer.PlayerClass playerClass)` - Limits the event to appearing when playing the specified character
- `spawnCondition(Condition spawnCondition)` - Checked at the start of an act to determine whether the event should be added to the pool
- `bonusCondition(Condition bonusCondition)` - Checked when a random event is rolled to determine whether the event can currently appear
- `overrideEvent(String overrideEventID)` - This will cause the event to override the specified event
- `eventType(EventUtils.EventType eventType)` - Event types: `NORMAL`, `ONE_TIME`, `SHRINE`, `OVERRIDE`, `FULL_REPLACE`
- `endsWithRewardsUI(bool value)` - If your event ends with rewards UI (combat), set to true

## PhasedEvent

The `PhasedEvent` class treats each individual interaction as a separate "phase" which can have their properties individually defined.

```java
public class PhasedEventExample extends PhasedEvent {
    public static final String ID = "Phased Event Example";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString(ID);
    private static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    private static final String[] OPTIONS = eventStrings.OPTIONS;
    private static final String title = eventStrings.NAME;

    public PhasedEventExample() {
        super(ID, title, "img/events/eventpicture.png");

        registerPhase("Start", new TextPhase("Body Text")
            .addOption("Option 1", (i)->transitionKey("Phase 2"))
            .addOption("Option 2", (i)->transitionKey("Phase 3")));

        registerPhase("Phase 2", new TextPhase(DESCRIPTIONS[0])
            .addOption(OPTIONS[0], (i)->openMap()));

        registerPhase("Phase 3", new CombatPhase(MonsterHelper.CULTIST_ENC)
            .addRewards(true, (room)->room.addRelicToRewards(AbstractRelic.RelicTier.RARE))
            .setNextKey("Phase 2"));

        transitionKey("Start");
    }
}
```

Phase types: `TextPhase`, `CombatPhase`, `InteractionPhase`.

When a phase ends, it should call `transitionKey` to move to another phase, or `openMap` to end the event. If neither happens, your event will cause a softlock.
