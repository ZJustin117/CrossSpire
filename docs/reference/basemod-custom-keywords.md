# BaseMod Custom Keywords Documentation

## Overview

Keywords are things like "Block", "Retain", "Weak", "Vulnerable", etc. that are highlighted and have tooltip information associated with them.

## API

`addKeyword(String[] names, String description)` - only call this in the `receiveEditKeywords` callback from `EditKeywordsSubscriber`

- `names` - This should be an array of similar words (must be all lowercase)
- `description` - The description for the keyword

## Usage (Recommended: JSON-based)

Keywords should be stored in a JSON file (usually named `keywords.json`) and the `receiveEditKeywords` method should load and register all keywords.

```java
public static String localizationPath(String lang, String file) {
    return resourcesFolder + "/localization/" + lang + "/" + file;
}

@Override
public void receiveEditKeywords() {
    Gson gson = new Gson();
    String json = Gdx.files.internal(
        localizationPath(Settings.language, "keywords.json")
    ).readString(String.valueOf(StandardCharsets.UTF_8));
    Keyword[] keywords = gson.fromJson(json, Keyword[].class);

    if (keywords != null) {
        for (Keyword keyword : keywords) {
            BaseMod.addKeyword(modID.toLowerCase(), keyword.PROPER_NAME,
                keyword.NAMES, keyword.DESCRIPTION);
        }
    }
}
```

## Manual keyword creation (deprecated)

```java
BaseMod.addKeyword({"ice"}, "Will apply a buff to the next attack with Ice in its description.");
```

Note: Manual creation means your mod will not support localizing keywords in other languages.
