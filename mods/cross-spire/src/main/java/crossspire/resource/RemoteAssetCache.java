package crossspire.resource;

import com.badlogic.gdx.graphics.Texture;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import basemod.BaseMod;

public class RemoteAssetCache {

    private static final int MAX_MEMORY_ENTRIES = 64;
    private static final Map<String, Texture> memoryCache = new ConcurrentHashMap<String, Texture>();
    private static File diskRoot;

    static {
        diskRoot = new File("crossspire_cache");
        if (!diskRoot.exists()) diskRoot.mkdirs();
    }

    public static Texture getTexture(String sourcePlayerId, String resourceType, String resourceId) {
        String key = sourcePlayerId + "/" + resourceType + "/" + resourceId;
        Texture tex = memoryCache.get(key);
        if (tex != null) return tex;
        return null;
    }

    public static void putTexture(String sourcePlayerId, String resourceType, String resourceId, byte[] pngData) {
        if (memoryCache.size() >= MAX_MEMORY_ENTRIES) {
            memoryCache.clear();
        }
        String key = sourcePlayerId + "/" + resourceType + "/" + resourceId;
        try {
            Texture tex = new Texture(new com.badlogic.gdx.graphics.Pixmap(pngData, 0, pngData.length));
            memoryCache.put(key, tex);
        } catch (Exception e) {
            BaseMod.logger.error("RemoteAssetCache texture error: " + e.getMessage());
        }
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

    public static void clear() {
        memoryCache.clear();
    }
}
