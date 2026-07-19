package crossspire.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-combat registry of power/buff attachments with applier-first logic ownership.
 */
public final class ComponentAttachmentRegistry {

    private static final Map<String, ComponentAttachment> byInstance = new ConcurrentHashMap<>();

    private ComponentAttachmentRegistry() {}

    public static void clear() {
        byInstance.clear();
    }

    public static int size() {
        return byInstance.size();
    }

    public static ComponentAttachment register(ComponentAttachment attachment) {
        if (attachment == null || attachment.instanceId == null || attachment.instanceId.isEmpty()) {
            return null;
        }
        byInstance.put(attachment.instanceId, attachment);
        return attachment;
    }

    /**
     * Register or replace an apply_power attachment.
     * instance_id is stable per (host, power, logic_owner) so re-applies update amount.
     */
    public static ComponentAttachment registerApplyPower(
            String powerId,
            String logicOwnerId,
            String hostEntityId,
            int amount) {
        if (powerId == null || powerId.isEmpty()) return null;
        if (hostEntityId == null || hostEntityId.isEmpty()) return null;
        String owner = logicOwnerId != null ? logicOwnerId : "";
        String instanceId = hostEntityId + "|" + powerId + "|" + owner;
        if (owner.isEmpty()) {
            instanceId = hostEntityId + "|" + powerId + "|anon-" + UUID.randomUUID().toString().substring(0, 8);
        }
        ComponentAttachment att = ComponentAttachment.ofPower(instanceId, powerId, owner, hostEntityId, amount);
        return register(att);
    }

    public static ComponentAttachment get(String instanceId) {
        return byInstance.get(instanceId);
    }

    public static List<ComponentAttachment> getByHost(String hostEntityId) {
        if (hostEntityId == null) return Collections.emptyList();
        List<ComponentAttachment> out = new ArrayList<>();
        for (ComponentAttachment a : byInstance.values()) {
            if (hostEntityId.equals(a.hostEntityId)) out.add(a);
        }
        return out;
    }

    public static List<ComponentAttachment> getByLogicOwner(String logicOwnerId) {
        if (logicOwnerId == null || logicOwnerId.isEmpty()) return Collections.emptyList();
        List<ComponentAttachment> out = new ArrayList<>();
        for (ComponentAttachment a : byInstance.values()) {
            if (logicOwnerId.equals(a.logicOwnerId)) out.add(a);
        }
        return out;
    }

    public static boolean remove(String instanceId) {
        return byInstance.remove(instanceId) != null;
    }

    public static List<ComponentAttachment> all() {
        return new ArrayList<>(byInstance.values());
    }

    /** Map host entity id from effect target string. */
    public static String hostEntityIdForTarget(String target) {
        if (target == null || target.isEmpty() || "self".equals(target)) {
            return "player:local";
        }
        if (target.startsWith("player:") || target.startsWith("monster:")) {
            return target;
        }
        return "monster:" + target;
    }

    /** Snapshot for tests / diagnostics. */
    public static Map<String, ComponentAttachment> snapshot() {
        return new LinkedHashMap<>(byInstance);
    }
}
