package crossspire.reference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import basemod.BaseMod;

public final class ContentValidator {

    public static final String ALG = "SHA-256";

    public static String hashResource(String resourceType, String resourceId) {
        try {
            String path = resolvePath(resourceType, resourceId);
            if (path == null) return hashFromInstance(resourceType, resourceId);
            byte[] bytes = loadBytes(path);
            return sha256(bytes);
        } catch (Exception e) {
            return hashFromInstance(resourceType, resourceId);
        }
    }

    public static boolean matches(String resourceType, String resourceId, String remoteHash) {
        if (remoteHash == null || remoteHash.isEmpty()) return false;
        String local = hashResource(resourceType, resourceId);
        if (local.isEmpty()) return false;
        return local.equals(remoteHash);
    }

    /** Hashes a loadable class for event and other class-addressed content validation. */
    public static String hashClass(String className) {
        if (className == null || className.isEmpty()) return "";
        try {
            Class<?> cls = Class.forName(className);
            String resource = "/" + className.replace('.', '/') + ".class";
            InputStream stream = cls.getResourceAsStream(resource);
            if (stream == null) return "";
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1) bytes.write(buffer, 0, read);
                return sha256(bytes.toByteArray());
            } finally {
                stream.close();
            }
        } catch (Exception e) {
            return "";
        }
    }

    private static String hashFromInstance(String resourceType, String resourceId) {
        try {
            Class<?> cls = null;
            if ("card".equals(resourceType)) {
                AbstractCard card = CardLibrary.getCard(resourceId);
                if (card != null) cls = card.getClass();
            } else if ("relic".equals(resourceType)) {
                AbstractRelic relic = RelicLibrary.getRelic(resourceId);
                if (relic != null) cls = relic.getClass();
            } else {
                String className = resolvePath(resourceType, resourceId);
                if (className != null) cls = Class.forName(className.replace('/', '.').replace(".class", ""));
            }
            if (cls == null) return "";
            String classPath = cls.getName().replace('.', '/') + ".class";
            byte[] bytes = loadBytes(classPath);
            return sha256(bytes);
        } catch (Exception e) {
            return "";
        }
    }

    private static String resolvePath(String resourceType, String resourceId) {
        if ("card".equals(resourceType)) {
            return "com/megacrit/cardcrawl/cards/" + toCamelCase(resourceId) + ".class";
        }
        if ("relic".equals(resourceType)) {
            return "com/megacrit/cardcrawl/relics/" + toCamelCase(resourceId) + ".class";
        }
        if ("power".equals(resourceType)) {
            return "com/megacrit/cardcrawl/powers/" + toCamelCase(resourceId) + "Power.class";
        }
        return null;
    }

    private static byte[] loadBytes(String classPath) throws IOException {
        ClassLoader cl = ContentValidator.class.getClassLoader();
        InputStream is = cl.getResourceAsStream(classPath);
        if (is == null) throw new IOException("resource not found: " + classPath);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALG);
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString().substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static String toCamelCase(String id) {
        if (id == null || id.isEmpty()) return id;
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("[\\s_\\-]+")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private ContentValidator() {}
}
