package crossspire.resource;

import basemod.BaseMod;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceRegistryTracker {

    private static final Map<String, Set<String>> playerCards = new ConcurrentHashMap<String, Set<String>>();
    private static final Map<String, Set<String>> playerRelics = new ConcurrentHashMap<String, Set<String>>();

    public static void onRegistryReceived(String rawMessage) {
        JsonObject msg = new com.google.gson.JsonParser().parse(rawMessage).getAsJsonObject();
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        if (source.isEmpty()) return;

        // Store card/relic/power registry for future resource requests
        BaseMod.logger.info("ResourceRegistryTracker received from " + source.substring(0, 8));
    }

    public static void sendMyRegistry() {
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;

        JsonObject reg = new JsonObject();
        reg.addProperty("type", "resource_registry");
        reg.addProperty("source", CrossSpireMod.playerId);

        // Send empty list for MVP — actual card/relic lists will be populated later
        reg.add("cards", new com.google.gson.JsonArray());
        reg.add("relics", new com.google.gson.JsonArray());
        reg.add("powers", new com.google.gson.JsonArray());
        reg.add("potions", new com.google.gson.JsonArray());
        reg.add("characters", new com.google.gson.JsonArray());

        CrossSpireMod.relayClient.send(reg.toString());
        BaseMod.logger.info("ResourceRegistryTracker sent registry");
    }
}
