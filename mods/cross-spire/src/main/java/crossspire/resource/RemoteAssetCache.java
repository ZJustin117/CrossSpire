package crossspire.resource;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonJson;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import basemod.BaseMod;

public class RemoteAssetCache {

    private static final int MAX_MEMORY_ENTRIES = 256;
    private static final long EXPIRE_MS = 30L * 24 * 60 * 60 * 1000;
    private static final Map<String, Texture> textureCache = Collections.synchronizedMap(
        new LinkedHashMap<String, Texture>(32, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Texture> eldest) {
                if (size() > MAX_MEMORY_ENTRIES) {
                    try { eldest.getValue().dispose(); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }
        });
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
        updateManifest(sourcePlayerId, resourceType, resourceId, data);
    }

    public static void writeDiskString(String sourcePlayerId, String resourceType, String resourceId, String text) {
        try {
            writeDisk(sourcePlayerId, resourceType, resourceId, text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            BaseMod.logger.error("RemoteAssetCache writeString error: " + e.getMessage());
        }
    }

    public static byte[] readDisk(String sourcePlayerId, String resourceType, String resourceId) {
        File file = new File(diskRoot, sourcePlayerId + "/" + resourceType + "/" + resourceId);
        if (!file.exists()) return null;
        if (isExpired(file)) {
            file.delete();
            return null;
        }
        try {
            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            String key = sourcePlayerId + "/" + resourceType + "/" + resourceId;
            String storedChecksum = getManifestChecksum(key);
            if (storedChecksum != null && !storedChecksum.isEmpty()) {
                String actualChecksum = sha256(data);
                if (!storedChecksum.equals(actualChecksum)) {
                    BaseMod.logger.info("RemoteAssetCache checksum mismatch: " + key);
                    file.delete();
                    return null;
                }
            }
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    public static String readDiskString(String sourcePlayerId, String resourceType, String resourceId) {
        byte[] data = readDisk(sourcePlayerId, resourceType, resourceId);
        if (data == null) return null;
        try {
            return new String(data, StandardCharsets.UTF_8);
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

    public static boolean verify(String sourcePlayerId, String resourceType, String resourceId, byte[] expectedData) {
        File file = new File(diskRoot, sourcePlayerId + "/" + resourceType + "/" + resourceId);
        if (!file.exists()) return false;
        try {
            byte[] existing = readDisk(sourcePlayerId, resourceType, resourceId);
            if (existing == null) return false;
            String expectedHash = sha256(expectedData);
            String actualHash = sha256(existing);
            return expectedHash.equals(actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isExpired(File file) {
        return System.currentTimeMillis() - file.lastModified() > EXPIRE_MS;
    }

    private static void updateManifest(String sourcePlayerId, String resourceType, String resourceId, byte[] data) {
        try {
            File manifest = new File(diskRoot, "manifest.json");
            Map<String, String> entries = new ConcurrentHashMap<>();
            if (manifest.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8);
                com.google.gson.JsonObject obj = new com.google.gson.JsonParser().parse(content).getAsJsonObject();
                for (Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
                    entries.put(e.getKey(), e.getValue().getAsString());
                }
            }
            String key = sourcePlayerId + "/" + resourceType + "/" + resourceId;
            entries.put(key, sha256(data));
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            for (Map.Entry<String, String> e : entries.entrySet()) {
                json.addProperty(e.getKey(), e.getValue());
            }
            java.nio.file.Files.write(manifest.toPath(), json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    static String sha256ForTest(byte[] data) {
        return sha256(data);
    }

    private static String getManifestChecksum(String key) {
        try {
            File manifest = new File(diskRoot, "manifest.json");
            if (!manifest.exists()) return null;
            String content = new String(java.nio.file.Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8);
            com.google.gson.JsonObject obj = new com.google.gson.JsonParser().parse(content).getAsJsonObject();
            if (obj.has(key)) return obj.get(key).getAsString();
        } catch (Exception ignored) {}
        return null;
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static void clear() {
        textureCache.clear();
        characterCache.clear();
    }
}
