package crossspire.resource;

import basemod.BaseMod;
import com.badlogic.gdx.graphics.Texture;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;

public class RemoteResourceManager {

    public static Texture getCardLarge(String sourcePlayerId, String cardId) {
        // L2 cache check
        byte[] cached = RemoteAssetCache.readDisk(sourcePlayerId, "card_large", cardId + ".png");
        if (cached != null) {
            RemoteAssetCache.putTexture(sourcePlayerId, "card_large", cardId, cached);
            Texture tex = RemoteAssetCache.getTexture(sourcePlayerId, "card_large", cardId);
            if (tex != null) return tex;
        }

        // Not cached — request from remote
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
        BaseMod.logger.info("RemoteResourceManager resource_response received");
    }
}
