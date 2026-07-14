package crossspire.resource;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonJson;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import basemod.BaseMod;

public class RemoteAssetCache {

    private static final int MAX_MEMORY_ENTRIES = 64;
    private static final Map<String, Texture> textureCache = new ConcurrentHashMap<String, Texture>();
    private static final Map<String, RemoteCharacterResource> characterCache = new ConcurrentHashMap<String, RemoteCharacterResource>();
    private static File diskRoot;

    static {
        diskRoot = new File("crossspire_cache");
        if (!diskRoot.exists()) diskRoot.mkdirs();
    }

    public static Texture getTexture(String sourcePlayerId, String resourceType, String resourceId) {
        String key = sourcePlayerId + "/" + resourceType + "/" + resourceId;
        return textureCache.get(key);
    }

    public static void putTexture(String sourcePlayerId, String resourceType, String resourceId, byte[] pngData) {
        if (textureCache.size() >= MAX_MEMORY_ENTRIES) {
            textureCache.clear();
        }
        String key = sourcePlayerId + "/" + resourceType + "/" + resourceId;
        try {
            Texture tex = new Texture(new com.badlogic.gdx.graphics.Pixmap(pngData, 0, pngData.length));
            textureCache.put(key, tex);
        } catch (Exception e) {
            BaseMod.logger.error("RemoteAssetCache texture error: " + e.getMessage());
        }
    }

    public static RemoteCharacterResource getCharacter(String sourcePlayerId) {
        return characterCache.get(sourcePlayerId);
    }

    public static void putCharacter(String sourcePlayerId, RemoteCharacterResource chr) {
        characterCache.put(sourcePlayerId, chr);
    }

    public static void clearCharacters() {
        characterCache.clear();
    }

    public static void writeDisk(String sourcePlayerId, String resourceType, String resourceId, byte[] data) {
        File dir = new File(diskRoot, sourcePlayerId + "/" + resourceType);
        dir.mkdirs();
        File file = new File(dir, resourceId);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        } catch (IOException e) {
            BaseMod.logger.error("RemoteAssetCache write error: " + e.getMessage());
        }
    }

    public static void writeDiskString(String sourcePlayerId, String resourceType, String resourceId, String text) {
        try {
            writeDisk(sourcePlayerId, resourceType, resourceId, text.getBytes("UTF-8"));
        } catch (Exception e) {
            BaseMod.logger.error("RemoteAssetCache writeString error: " + e.getMessage());
        }
    }

    public static byte[] readDisk(String sourcePlayerId, String resourceType, String resourceId) {
        File file = new File(diskRoot, sourcePlayerId + "/" + resourceType + "/" + resourceId);
        if (!file.exists()) return null;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    public static String readDiskString(String sourcePlayerId, String resourceType, String resourceId) {
        byte[] data = readDisk(sourcePlayerId, resourceType, resourceId);
        if (data == null) return null;
        try {
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean tryLoadCharacterFromDisk(String sourcePlayerId) {
        String skeletonJson = readDiskString(sourcePlayerId, "characters", "skeleton.json");
        String atlasText = readDiskString(sourcePlayerId, "characters", "skeleton.atlas");
        if (skeletonJson == null || atlasText == null) return false;

        try {
            File charDir = new File(diskRoot, sourcePlayerId + "/characters");
            File atlasFile = new File(charDir, "skeleton.atlas");

            TextureAtlas atlas = new TextureAtlas(
                new com.badlogic.gdx.files.FileHandle(atlasFile));

            SkeletonJson json = new SkeletonJson(atlas);
            com.badlogic.gdx.files.FileHandle jsonFile =
                new com.badlogic.gdx.files.FileHandle(new File(charDir, "skeleton.json"));
            SkeletonData sd = json.readSkeletonData(jsonFile);

            RemoteCharacterResource chr = new RemoteCharacterResource(sourcePlayerId);
            chr.skeletonData = sd;
            chr.atlas = atlas;
            chr.buildRenderables();
            characterCache.put(sourcePlayerId, chr);

            BaseMod.logger.info("RemoteAssetCache loaded character from disk: " + sourcePlayerId.substring(0, 8));
            return true;
        } catch (Exception e) {
            BaseMod.logger.error("RemoteAssetCache character load error: " + e.getMessage());
            return false;
        }
    }

    public static void clear() {
        textureCache.clear();
        characterCache.clear();
    }
}
