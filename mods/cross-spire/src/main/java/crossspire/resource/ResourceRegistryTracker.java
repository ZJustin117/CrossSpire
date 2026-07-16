package crossspire.resource;

import basemod.BaseMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import crossspire.CrossSpireMod;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceRegistryTracker {

    private static final Map<String, Set<String>> playerCards = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> playerRelics = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> playerPowers = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> playerPotions = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> playerCharacters = new ConcurrentHashMap<>();

    public static void onRegistryReceived(String rawMessage) {
        JsonObject msg = new com.google.gson.JsonParser().parse(rawMessage).getAsJsonObject();
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        if (source.isEmpty() || source.equals(CrossSpireMod.playerId)) return;

        if (msg.has("cards")) {
            JsonArray arr = msg.getAsJsonArray("cards");
            Set<String> set = ConcurrentHashMap.newKeySet();
            for (int i = 0; i < arr.size(); i++) set.add(arr.get(i).getAsString());
            playerCards.put(source, set);
        }
        if (msg.has("relics")) {
            JsonArray arr = msg.getAsJsonArray("relics");
            Set<String> set = ConcurrentHashMap.newKeySet();
            for (int i = 0; i < arr.size(); i++) set.add(arr.get(i).getAsString());
            playerRelics.put(source, set);
        }
        if (msg.has("powers")) {
            JsonArray arr = msg.getAsJsonArray("powers");
            Set<String> set = ConcurrentHashMap.newKeySet();
            for (int i = 0; i < arr.size(); i++) set.add(arr.get(i).getAsString());
            playerPowers.put(source, set);
        }
        if (msg.has("potions")) {
            JsonArray arr = msg.getAsJsonArray("potions");
            Set<String> set = ConcurrentHashMap.newKeySet();
            for (int i = 0; i < arr.size(); i++) set.add(arr.get(i).getAsString());
            playerPotions.put(source, set);
        }
        if (msg.has("characters")) {
            JsonArray arr = msg.getAsJsonArray("characters");
            Set<String> set = ConcurrentHashMap.newKeySet();
            for (int i = 0; i < arr.size(); i++) set.add(arr.get(i).getAsString());
            playerCharacters.put(source, set);
        }

        BaseMod.logger.info("ResourceRegistryTracker received from " + source.substring(0, 8)
            + " cards=" + playerCards.getOrDefault(source, new HashSet<>()).size()
            + " relics=" + playerRelics.getOrDefault(source, new HashSet<>()).size());
    }

    public static void sendMyRegistry() {
        if (!CrossSpireMod.isConnected()) return;

        JsonObject reg = new JsonObject();
        reg.addProperty("type", "resource_registry");
        reg.addProperty("source", CrossSpireMod.playerId);
        reg.add("cards", jsonArrayFrom(getLocalCardIds()));
        reg.add("relics", jsonArrayFrom(getLocalRelicIds()));
        reg.add("powers", jsonArrayFrom(getLocalPowerIds()));
        reg.add("potions", jsonArrayFrom(getLocalPotionIds()));
        reg.add("characters", jsonArrayFrom(new String[]{
            "IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"
        }));

        CrossSpireMod.send(reg.toString());
        BaseMod.logger.info("ResourceRegistryTracker sent registry cards=" + getLocalCardIds().length);
    }

    public static String[] getLocalCardIds() {
        Set<String> ids = new LinkedHashSet<>();
        try {
            for (Map.Entry<String, AbstractCard> e : CardLibrary.cards.entrySet()) {
                if (e.getValue() != null) ids.add(e.getKey());
            }
        } catch (Exception e) {
            return new String[0];
        }
        return ids.toArray(new String[0]);
    }

    public static String[] getLocalRelicIds() {
        Set<String> ids = new LinkedHashSet<>();
        try {
            for (Field f : RelicLibrary.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Map<?, ?> map = (Map<?, ?>) f.get(null);
                    if (map != null) {
                        for (Object v : map.values()) {
                            if (v instanceof AbstractRelic) ids.add(((AbstractRelic) v).relicId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            return new String[0];
        }
        return ids.toArray(new String[0]);
    }

    public static String[] getLocalPowerIds() {
        Set<String> ids = new LinkedHashSet<>();
        try {
            for (Map.Entry<String, AbstractCard> e : CardLibrary.cards.entrySet()) {
                AbstractCard c = e.getValue();
                if (c != null && c.type == AbstractCard.CardType.POWER) ids.add(e.getKey());
            }
        } catch (Exception e) {
            return new String[0];
        }
        return ids.toArray(new String[0]);
    }

    public static String[] getLocalPotionIds() {
        Set<String> ids = new LinkedHashSet<>();
        try {
            ArrayList<String> raw = PotionHelper.getPotions(null, false);
            for (String id : raw) {
                AbstractPotion p = PotionHelper.getPotion(id);
                if (p != null && p.ID != null) ids.add(p.ID);
            }
        } catch (Exception e) {
            return new String[0];
        }
        return ids.toArray(new String[0]);
    }

    private static JsonArray jsonArrayFrom(String[] values) {
        JsonArray arr = new JsonArray();
        for (String v : values) arr.add(v);
        return arr;
    }

    public static boolean hasCard(String playerId, String cardId) {
        Set<String> set = playerCards.get(playerId);
        return set != null && set.contains(cardId);
    }

    public static boolean hasRelic(String playerId, String relicId) {
        Set<String> set = playerRelics.get(playerId);
        return set != null && set.contains(relicId);
    }

    public static Set<String> getCards(String playerId) {
        return playerCards.getOrDefault(playerId, new HashSet<>());
    }

    public static Set<String> getRelics(String playerId) {
        return playerRelics.getOrDefault(playerId, new HashSet<>());
    }
}
