# ModTheSpire SpirePatch Documentation

SpirePatch allows mods to patch their own code into Slay The Spire. When loading a mod, ModTheSpire searches through every class in the mod for any that have the `SpirePatch` annotation. For each method you want to patch with your mod, you must create a new class and annotate it with `SpirePatch`.

**There is also a newer [SpirePatch2](https://github.com/kiooeht/ModTheSpire/wiki/SpirePatch2), which changes how patch parameters are handled.**

ModTheSpire currently supports the following patch types:
- Prefix
- Postfix
- Insert
- Instrument
- Replace
- Raw

## General Rules

- If a patch class is a **nested** class, it must be a **static** class.
- A patch method must be a **static** method.
- Use the `@SpirePatch` annotation on the patch class.
- Patch methods are passed all the arguments of the original method as well as the instance if original method is not static (instance first, then parameters).

```java
public static void [PatchMethod]([InstanceType] __instance, [parameters]...) { ... }
```

### @SpirePatch Parameters

- **clz** - Defines the class that contains the method to be patched.
- **cls** (Old way) - Defines the class that contains the method to be patched. Must be the complete class path and class name.
- **method** - Defines the method to be patched.
  - Use `SpirePatch.CONSTRUCTOR` to target a constructor.
  - Use `SpirePatch.STATICINITIALIZER` to target a static initializer.
- **paramtypez** - Defines the parameter types of the method to be patched (only necessary if multiple methods with the same name exist).
- **paramtypes** (Old way) - Defines the parameter types of the method to be patched (only necessary if multiple methods with the same name exist). Type names must be complete class path and class name.
- **requiredModId** - Defines a Mod ID for a mod that must be loaded for this patch to apply. Used for cross-mod patching. Will generate an error if the mod is loaded but the patch fails.
- **optional** - When set to true, if the class+method to be patched does not exist (e.g. patching another mod that isn't loaded) the patch will be ignored. Will NOT generate an error if the patch fails.

```java
@SpirePatch(
    clz=AbstractPlayer.class,
    method="useCard",
    paramtypez={
        AbstractCard.class,
        AbstractMonster.class,
        int.class
    }
)
public class ExamplePatch {
    ...
}
```

## Patching Order

Patches are applied first in order of type, then in order of mod. Patch type is ordered: Insert, Instrument, Replace, Prefix, Postfix, Raw.

## Prefix

Prefix patching inserts a call to your `Prefix` method at the start of the method you are patching.

You may also use the `@SpirePrefixPatch` annotation to denote a method to be a Prefix.

```java
public static void Prefix(Ironclad __instance) { ... }

@SpirePrefixPatch
public static void Foobar(Ironclad __instance) { ... }
```

Features: @ByRef, Private Field Captures, SpireReturn

## Postfix

Postfix patching inserts a call to your `Postfix` method at the end of the method you are patching. Postfix patches can also change the return value of the patched method.

```java
public static void Postfix(Ironclad __instance)
// or
public static ArrayList<String> Postfix(ArrayList<String> __result, Ironclad __instance) {
    __result.add("Example Card");
    return __result;
}

@SpirePostfixPatch
public static void Foobar(Ironclad __instance)
```

Features: @ByRef, Private Field Captures

## Insert

Insert patching inserts a call to your `Insert` method in middle of the method you are patching. An `Insert` method must be accompanied by the @SpireInsertPatch.

### @SpireInsertPatch Parameters

Either `loc` or `rloc` or `locator` *must* be given. The patch method will be called directly before the line number specified.

- **loc** - Defines the absolute line number to insert at, absolute to the start of the file.
- **rloc** - Defines the line number to insert at, relative to the start of the method to be patched.
- **locs** - Defines an additional array of line numbers to insert at, absolute to the start of the file.
- **rlocs** - Defines an additional array of line numbers to insert at, relative to the start of the method to be patched.
- **localvars** - Used to capture any local variables and pass them to the patch method.

```java
@SpireInsertPatch(
    loc=123,
    localvars={"example"}
)
public static void Insert(Ironclad __instance, String param1, String param2, int example) { ... }
```

### Locator

Since Slay The Spire is on a weekly update schedule, line numbers are subject to change. A `Locator` patches based on *game logic* rather than line numbers. Use the `locator` parameter of `@SpireInsertPatch`.

```java
@SpirePatch(clz=CardCrawlGame.class, method="render")
public class PostRenderHook {
    @SpireInsertPatch(locator=Locator.class, localvars={"sb"})
    public static void Insert(CardCrawlGame __instance, SpriteBatch sb) {
        // draw things right before the SpriteBatch has `end` called
    }

    private static class Locator extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher finalMatcher = new Matcher.MethodCallMatcher(SpireBatch.class, "end");
            return LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), finalMatcher);
        }
    }
}
```

## Instrument

Instrument patching gives you more access to javassist's API, allowing you to alter code in the method you are patching.

```java
import javassist.expr.ExprEditor;

public static ExprEditor Instrument() {
    return new ExprEditor() { ... };
}

@SpireInstrumentPatch
public static ExprEditor Foobar() {
    return new ExprEditor() { ... };
}
```

## Replace

A Replace patch will completely replace a method with your own.

```java
@SpirePatch(clz=CardLibrary.class, method="getCardList")
public class GetCardList {
    public static Object Replace(LibraryType type) { ... }
}
```

**Warning: DO NOT use a Replace patch unless absolutely necessary. The destructive nature of Replace patches mean you override any other patches applied to the method you're patching.**

## Raw

Raw patches give you access to the underlying Javassist API to make lower-level changes.

```java
public static void Raw(CtBehavior ctMethodToPatch) { ... }

@SpireRawPatch
public static void fooBar(CtBehavior ctMethodToPatch) { ... }
```
