package crossspire.resource;

import basemod.BaseMod;
import basemod.ReflectionHacks;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.PixmapIO;
import com.esotericsoftware.spine.Skeleton;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class RemoteAssetServer {

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
                BaseMod.logger.info("RemoteAssetServer serveResource: asset not cached " + resourceType + "/" + resourceId);
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

            switch (resourceType) {
                case "character_skeleton":
                    Skeleton skel = (Skeleton) ReflectionHacks.getPrivate(player,
                        com.megacrit.cardcrawl.core.AbstractCreature.class, "skeleton");
                    if (skel == null) return;
                    String skeletonJson = skel.getData().hashCode() + "";
                    sendResponse(requester, resourceType, "skeleton.json", skeletonJson);
                    break;

                case "character_atlas": {
                    TextureAtlas atlas = (TextureAtlas) ReflectionHacks.getPrivate(player,
                        com.megacrit.cardcrawl.core.AbstractCreature.class, "atlas");
                    if (atlas == null) return;

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(baos, "UTF-8");
                    try {
                        for (com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion r :
                                atlas.getRegions()) {
                            writer.write(r.name + "\n");
                            writer.write("  rotate: false\n");
                            writer.write("  xy: " + (int)r.getRegionX() + ", " + (int)r.getRegionY() + "\n");
                            writer.write("  size: " + r.getRegionWidth() + ", " + r.getRegionHeight() + "\n");
                            writer.write("  orig: " + r.originalWidth + ", " + r.originalHeight + "\n");
                            writer.write("  offset: " + (int)r.offsetX + ", " + (int)r.offsetY + "\n");
                            writer.write("  index: -1\n");
                        }
                        writer.flush();
                    } catch (Exception ignored) {}
                    sendResponse(requester, resourceType, "skeleton.atlas",
                        Base64.getEncoder().encodeToString(baos.toByteArray()));
                    break;
                }

                case "character_png": {
                    TextureAtlas atlas = (TextureAtlas) ReflectionHacks.getPrivate(player,
                        com.megacrit.cardcrawl.core.AbstractCreature.class, "atlas");
                    if (atlas == null) return;
                    com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion first =
                        atlas.getRegions().first();
                    if (first == null) return;
                    Texture tex = first.getTexture();
                    if (tex == null) return;
                    tex.getTextureData().prepare();
                    Pixmap pixmap = tex.getTextureData().consumePixmap();

                    java.io.File tmpFile = java.io.File.createTempFile("spine_", ".png");
                    com.badlogic.gdx.files.FileHandle fh =
                        new com.badlogic.gdx.files.FileHandle(tmpFile);
                    PixmapIO.writePNG(fh, pixmap);

                    byte[] pngData = java.nio.file.Files.readAllBytes(tmpFile.toPath());
                    tmpFile.delete();

                    sendResponse(requester, resourceType, "skeleton.png",
                        Base64.getEncoder().encodeToString(pngData));
                    break;
                }
            }
        } catch (Exception e) {
            BaseMod.logger.error("RemoteAssetServer character asset error: " + e.getMessage());
        }
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
