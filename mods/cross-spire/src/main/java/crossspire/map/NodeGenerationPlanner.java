package crossspire.map;

import crossspire.event.EventInterfaceFactory;
import crossspire.network.Protocol;

/** Pure, deterministic first-pass node content planner (NIH-owned, no live STS RNG). */
public final class NodeGenerationPlanner {

    private NodeGenerationPlanner() {}

    public static Protocol.NodeGenerationResult plan(MapNode node, Protocol.NodeInstanceInfo instance) {
        if (node == null || instance == null || node.nodeId == null
            || !node.nodeId.equals(instance.nodeId)) {
            return null;
        }
        String roomType = node.roomType;
        if ("unknown".equals(roomType) || "?".equals(roomType)) {
            roomType = resolveUnknown(node.nodeId);
        }
        if ("start".equals(roomType)) return null;

        Protocol.NodeGenerationResult result = new Protocol.NodeGenerationResult();
        result.nodeId = node.nodeId;
        result.roomType = roomType;
        if ("monster".equals(roomType)) {
            result.encounter = "Cultist";
            return result;
        }
        if ("elite".equals(roomType)) {
            result.encounter = eliteEncounter(node);
            return result;
        }
        if ("boss".equals(roomType)) {
            result.encounter = "The Guardian";
            return result;
        }
        if ("event".equals(roomType)) {
            result.eventInterface = EventInterfaceFactory.create(instance);
            return result.eventInterface != null ? result : null;
        }
        if ("shop".equals(roomType)) {
            // Local-only seed hint. Multiplayer SoT is room_type + instance id only (T9).
            result.shopSeed = shopSeed(instance);
            return result;
        }
        if ("rest".equals(roomType)) {
            // Local-only option hint. Multiplayer SoT is room_type + instance id only (T9).
            result.restOptions = restOptions(instance.nodeInstanceId);
            return result;
        }
        if ("treasure".equals(roomType)) {
            result.treasureTier = treasureTier(node, instance);
            return result;
        }
        return null;
    }

    /** Deterministic first-pass '?' resolution without consuming map RNG. */
    static String resolveUnknown(String nodeId) {
        int hash = nodeId != null ? nodeId.hashCode() : 0;
        return (hash & 1) == 0 ? "event" : "monster";
    }

    static String shopSeed(Protocol.NodeInstanceInfo instance) {
        String key = instance.nodeInstanceId != null ? instance.nodeInstanceId : instance.nodeId;
        return "shop:" + Integer.toHexString(stableHash(key));
    }

    static String[] restOptions(String nodeInstanceId) {
        // Base campfire options; "toke" appears deterministically for some instances.
        if ((stableHash(nodeInstanceId) & 3) == 0) {
            return new String[] {"rest", "smith", "recall", "toke"};
        }
        return new String[] {"rest", "smith", "recall"};
    }

    static String treasureTier(MapNode node, Protocol.NodeInstanceInfo instance) {
        int h = stableHash((instance != null ? instance.nodeInstanceId : "") + ":" + node.nodeId);
        int bucket = Math.abs(h) % 3;
        if (bucket == 0) return "small_chest";
        if (bucket == 1) return "medium_chest";
        return "large_chest";
    }

    static String eliteEncounter(MapNode node) {
        if (node != null && node.burningElite) return "Gremlin Nob";
        int h = stableHash(node != null ? node.nodeId : "elite");
        int bucket = Math.abs(h) % 3;
        if (bucket == 0) return "Gremlin Nob";
        if (bucket == 1) return "Lagavulin";
        return "3 Sentries";
    }

    private static int stableHash(String value) {
        if (value == null) return 0;
        int h = 0;
        for (int i = 0; i < value.length(); i++) {
            h = 31 * h + value.charAt(i);
        }
        return h;
    }
}
