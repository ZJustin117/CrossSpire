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

    public static RemoteCharacterResource getCharacter(String sourcePlayerId) {
        RemoteCharacterResource chr = RemoteAssetCache.getCharacter(sourcePlayerId);
        if (chr != null && chr.isLoaded()) return chr;

        if (RemoteAssetCache.tryLoadCharacterFromDisk(sourcePlayerId)) {
            return RemoteAssetCache.getCharacter(sourcePlayerId);
        }

        requestCharacterAssets(sourcePlayerId);
        return null;
    }

    private static void requestCharacterAssets(String targetPlayerId) {
        requestResource(targetPlayerId, "character_skeleton", "skeleton.json");
        requestResource(targetPlayerId, "character_atlas", "skeleton.atlas");
        requestResource(targetPlayerId, "character_png", "skeleton.png");
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
        BaseMod.logger.info("RemoteResourceManager requesting " + resourceType + "/" + resourceId
            + " from " + targetPlayerId.substring(0, 8));
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

            byte[] data = Base64.getDecoder().decode(dataB64);
            if (data.length == 0) return;

            if (resourceType.startsWith("character_")) {
                handleCharacterAsset(source, resourceType, resourceId, data);
                return;
            }

            String diskId = resourceId;
            if (!diskId.endsWith(".png")) diskId = diskId + ".png";
            RemoteAssetCache.writeDisk(source, resourceType, diskId, data);
            RemoteAssetCache.putTexture(source, resourceType, resourceId, data);

            BaseMod.logger.info("RemoteResourceManager cached " + resourceType + "/" + resourceId
                + " from " + source.substring(0, 8) + " (" + data.length + " bytes)");
        } catch (Exception e) {
            BaseMod.logger.error("RemoteResourceManager onResourceResponse error: " + e.getMessage());
        }
    }

    private static void handleCharacterAsset(String source, String resourceType,
                                             String resourceId, byte[] data) {
        String diskId = resourceId;
        RemoteAssetCache.writeDisk(source, "characters", diskId, data);

        BaseMod.logger.info("RemoteResourceManager cached character " + resourceType
            + " from " + source.substring(0, 8) + " (" + data.length + " bytes)");

        if ("skeleton.png".equals(resourceId)) {
            RemoteAssetCache.tryLoadCharacterFromDisk(source);
        }
    }

    public static void serveResource(String rawMessage) {
        RemoteAssetServer.serveResource(rawMessage);
    }
}
