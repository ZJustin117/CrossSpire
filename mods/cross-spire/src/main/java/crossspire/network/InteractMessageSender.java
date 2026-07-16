package crossspire.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Builds interact_request / interact_response messages for owner interaction selection.
 * See spec US-7 (FR-4.6).
 *
 * Flow: owner needs player interaction (e.g. select a card) →
 *   interact_request → host → caller player →
 *   caller renders selection UI →
 *   interact_response → host → owner →
 *   owner continues execution with selected items.
 */
public final class InteractMessageSender {

    private static final Gson GSON = new Gson();

    private InteractMessageSender() {}

    public static String buildInteractRequest(
            String source, String selectType, String[] options,
            String prompt, int minSelect, int maxSelect) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "interact_request");
        obj.addProperty("source", source);
        obj.addProperty("select_type", selectType);
        obj.addProperty("prompt", prompt);
        obj.addProperty("min_select", minSelect);
        obj.addProperty("max_select", maxSelect);

        JsonArray arr = new JsonArray();
        for (String o : options) arr.add(o);
        obj.add("options", arr);

        return GSON.toJson(obj);
    }

    public static String buildInteractResponse(String source, String[] selected) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "interact_response");
        obj.addProperty("source", source);

        JsonArray arr = new JsonArray();
        for (String s : selected) arr.add(s);
        obj.add("selected", arr);

        return GSON.toJson(obj);
    }
}
