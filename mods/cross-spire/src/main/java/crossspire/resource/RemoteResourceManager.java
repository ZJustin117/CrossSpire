package crossspire.resource;

import basemod.BaseMod;
import com.badlogic.gdx.graphics.Texture;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;
import java.util.Base64;

public class RemoteResourceManager {

    public static Texture getCardLarge(String sourcePlayerId, String cardId) {
        byte[] cached = RemoteAssetCache.readDisk(sourcePlayerId, "card_large", cardId + ".png");
        if (cached != null) {
            RemoteAssetCache.putTexture(sourcePlayerId, "card_large", cardId, cached);
            Texture tex = RemoteAssetCache.getTexture(sourcePlayerId, "card_large", cardId);
            if (tex != null) return tex;
        }

        requestResource(sourcePlayerId, "card_large", cardId);
        return null;
    }

    public static Texture getCardSmall(String sourcePlayerId, String cardId) {
        byte[] cached = RemoteAssetCache.readDisk(sourcePlayerId, "card_small", cardId + ".png");
        if (cached != null) {
            RemoteAssetCache.putTexture(sourcePlayerId, "card_small", cardId, cached);
            return RemoteAssetCache.getTexture(sourcePlayerId, "card_small", cardId);
        }
        requestResource(sourcePlayerId, "card_small", cardId);
        return null;
    }

    private static void requestResource(String targetPlayerId, String resourceType, String resourceId) {
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;

        JsonObject req = new JsonObject();
        req.addProperty("type", "resource_request");
        req.addProperty("source", CrossSpireMod.playerId);
        req.addProperty("target", targetPlayerId);
        req.addProperty("resource_type", resourceType);
        req.addProperty("resource_id", resourceId);

        CrossSpireMod.relayClient.send(req.toString());
        BaseMod.logger.info("RemoteResourceManager requesting " + resourceType + "/" + resourceId + " from " + targetPlayerId.substring(0, 8));
    }

    public static void onResourceResponse(String rawMessage) {
        try {
            JsonObject msg = new com.google.gson.JsonParser().parse(rawMessage).getAsJsonObject();
            String source = msg.has("source") ? msg.get("source").getAsString() : "";
            String resourceType = msg.has("resource_type") ? msg.get("resource_type").getAsString() : "";
            String resourceId = msg.has("resource_id") ? msg.get("resource_id").getAsString() : "";
            String dataB64 = msg.has("data") ? msg.get("data").getAsString() : "";

            if (source.isEmpty() || resourceType.isEmpty() || resourceId.isEmpty() || dataB64.isEmpty()) {
                BaseMod.logger.info("RemoteResourceManager onResourceResponse incomplete fields");
                return;
            }

            byte[] pngData = Base64.getDecoder().decode(dataB64);
            if (pngData.length == 0) return;

            String diskId = resourceId;
            if (!diskId.endsWith(".png")) diskId = diskId + ".png";

            RemoteAssetCache.writeDisk(source, resourceType, diskId, pngData);
            RemoteAssetCache.putTexture(source, resourceType, resourceId, pngData);

            BaseMod.logger.info("RemoteResourceManager cached " + resourceType + "/" + resourceId
                + " from " + source.substring(0, 8) + " (" + pngData.length + " bytes)");
        } catch (Exception e) {
            BaseMod.logger.error("RemoteResourceManager onResourceResponse error: " + e.getMessage());
        }
    }

    public static void serveResource(String rawMessage) {
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;

        try {
            JsonObject req = new com.google.gson.JsonParser().parse(rawMessage).getAsJsonObject();
            String requester = req.has("source") ? req.get("source").getAsString() : "";
            String resourceType = req.has("resource_type") ? req.get("resource_type").getAsString() : "";
            String resourceId = req.has("resource_id") ? req.get("resource_id").getAsString() : "";

            if (requester.isEmpty() || resourceType.isEmpty() || resourceId.isEmpty()) return;

            String lookId = resourceId.endsWith(".png") ? resourceId : resourceId + ".png";
            byte[] cached = RemoteAssetCache.readDisk(CrossSpireMod.playerId, resourceType, lookId);
            if (cached == null || cached.length == 0) {
                BaseMod.logger.info("RemoteResourceManager serveResource: asset not cached " + resourceType + "/" + resourceId);
                return;
            }

            JsonObject resp = new JsonObject();
            resp.addProperty("type", "resource_response");
            resp.addProperty("source", CrossSpireMod.playerId);
            resp.addProperty("target", requester);
            resp.addProperty("resource_type", resourceType);
            resp.addProperty("resource_id", resourceId);
            resp.addProperty("data", Base64.getEncoder().encodeToString(cached));

            CrossSpireMod.relayClient.send(resp.toString());
            BaseMod.logger.info("RemoteResourceManager served " + resourceType + "/" + resourceId
                + " -> " + requester.substring(0, 8) + " (" + cached.length + " bytes)");
        } catch (Exception e) {
            BaseMod.logger.error("RemoteResourceManager serveResource error: " + e.getMessage());
        }
    }
}
