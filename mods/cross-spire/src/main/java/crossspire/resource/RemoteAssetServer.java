package crossspire.resource;

import basemod.BaseMod;
import basemod.ReflectionHacks;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class RemoteAssetServer {

    private static final Map<String, String> CHAR_SKELETON_PATHS = new HashMap<>();
    static {
        CHAR_SKELETON_PATHS.put("IRONCLAD", "images/characters/ironclad/idle/skeleton.json");
        CHAR_SKELETON_PATHS.put("THE_SILENT", "images/characters/theSilent/idle/skeleton.json");
        CHAR_SKELETON_PATHS.put("DEFECT", "images/characters/defect/idle/skeleton.json");
        CHAR_SKELETON_PATHS.put("WATCHER", "images/characters/watcher/idle/skeleton.json");
    }
    private static final Map<String, String> CHAR_ATLAS_PATHS = new HashMap<>();
    static {
        CHAR_ATLAS_PATHS.put("IRONCLAD", "images/characters/ironclad/idle/skeleton.atlas");
        CHAR_ATLAS_PATHS.put("THE_SILENT", "images/characters/theSilent/idle/skeleton.atlas");
        CHAR_ATLAS_PATHS.put("DEFECT", "images/characters/defect/idle/skeleton.atlas");
        CHAR_ATLAS_PATHS.put("WATCHER", "images/characters/watcher/idle/skeleton.atlas");
    }
    private static final Map<String, String> CHAR_PNG_PATHS = new HashMap<>();
    static {
        CHAR_PNG_PATHS.put("IRONCLAD", "images/characters/ironclad/idle/skeleton.png");
        CHAR_PNG_PATHS.put("THE_SILENT", "images/characters/theSilent/idle/skeleton.png");
        CHAR_PNG_PATHS.put("DEFECT", "images/characters/defect/idle/skeleton.png");
        CHAR_PNG_PATHS.put("WATCHER", "images/characters/watcher/idle/skeleton.png");
    }

    public static void serveResource(String rawMessage) {
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;

        try {
            JsonObject req = new com.google.gson.JsonParser().parse(rawMessage).getAsJsonObject();
            String requester = req.has("source") ? req.get("source").getAsString() : "";
            String resourceType = req.has("resource_type") ? req.get("resource_type").getAsString() : "";
            String resourceId = req.has("resource_id") ? req.get("resource_id").getAsString() : "";

            if (requester.isEmpty() || resourceType.isEmpty() || resourceId.isEmpty()) return;

            if (resourceType.startsWith("character_")) {
                serveCharacterAsset(requester, resourceType);
                return;
            }

            String lookId = resourceId.endsWith(".png") ? resourceId : resourceId + ".png";
            byte[] cached = RemoteAssetCache.readDisk(CrossSpireMod.playerId, resourceType, lookId);
            if (cached == null || cached.length == 0) {
                BaseMod.logger.info("RemoteAssetServer no asset: " + resourceType + "/" + resourceId);
                return;
            }

            sendResponse(requester, resourceType, resourceId, Base64.getEncoder().encodeToString(cached));
        } catch (Exception e) {
            BaseMod.logger.error("RemoteAssetServer serveResource error: " + e.getMessage());
        }
    }

    private static void serveCharacterAsset(String requester, String resourceType) {
        try {
            com.megacrit.cardcrawl.characters.AbstractPlayer player =
                com.megacrit.cardcrawl.dungeons.AbstractDungeon.player;
            if (player == null) player = CrossSpireMod.localPlayer;
            if (player == null) return;

            String charClass = player.chosenClass != null ? player.chosenClass.name() : "IRONCLAD";

            switch (resourceType) {
                case "character_skeleton": {
                    String path = CHAR_SKELETON_PATHS.get(charClass);
                    if (path == null) return;
                    String json = readGameFile(path);
                    if (json != null) {
                        sendResponse(requester, resourceType, "skeleton.json",
                            Base64.getEncoder().encodeToString(json.getBytes("UTF-8")));
                    }
                    break;
                }
                case "character_atlas": {
                    String path = CHAR_ATLAS_PATHS.get(charClass);
                    if (path == null) return;
                    String atlasText = readGameFile(path);
                    if (atlasText != null) {
                        sendResponse(requester, resourceType, "skeleton.atlas",
                            Base64.getEncoder().encodeToString(atlasText.getBytes("UTF-8")));
                    }
                    break;
                }
                case "character_png": {
                    String path = CHAR_PNG_PATHS.get(charClass);
                    if (path == null) return;
                    FileHandle fh = Gdx.files.internal(path);
                    if (!fh.exists()) return;
                    Pixmap pixmap = new Pixmap(fh);
                    java.io.File tmp = java.io.File.createTempFile("spine_", ".png");
                    com.badlogic.gdx.files.FileHandle tmpFh = new com.badlogic.gdx.files.FileHandle(tmp);
                    PixmapIO.writePNG(tmpFh, pixmap);
                    byte[] data = java.nio.file.Files.readAllBytes(tmp.toPath());
                    tmp.delete();
                    pixmap.dispose();
                    sendResponse(requester, resourceType, "skeleton.png",
                        Base64.getEncoder().encodeToString(data));
                    break;
                }
            }
        } catch (Exception e) {
            BaseMod.logger.error("RemoteAssetServer character asset error: " + e.getMessage());
        }
    }

    private static String readGameFile(String path) {
        try {
            FileHandle fh = Gdx.files.internal(path);
            if (fh != null && fh.exists()) {
                return fh.readString("UTF-8");
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void sendResponse(String target, String resourceType, String resourceId, String dataB64) {
        JsonObject resp = new JsonObject();
        resp.addProperty("type", "resource_response");
        resp.addProperty("source", CrossSpireMod.playerId);
        resp.addProperty("target", target);
        resp.addProperty("resource_type", resourceType);
        resp.addProperty("resource_id", resourceId);
        resp.addProperty("data", dataB64);

        CrossSpireMod.relayClient.send(resp.toString());
        BaseMod.logger.info("RemoteAssetServer served " + resourceType + "/" + resourceId
            + " -> " + target.substring(0, 8));
    }
}
