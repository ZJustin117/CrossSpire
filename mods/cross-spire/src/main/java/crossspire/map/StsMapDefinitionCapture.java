package crossspire.map;

import basemod.BaseMod;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
import com.megacrit.cardcrawl.rooms.MonsterRoomElite;
import com.megacrit.cardcrawl.rooms.RestRoom;
import com.megacrit.cardcrawl.rooms.ShopRoom;
import com.megacrit.cardcrawl.rooms.TreasureRoom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Captures only immutable topology and display metadata from a MapHost's generated STS map. */
public final class StsMapDefinitionCapture {

    private StsMapDefinitionCapture() {}

    public static MapDefinition capture(String mapInstanceId, int revision, String digest) {
        if (empty(mapInstanceId) || AbstractDungeon.map == null || AbstractDungeon.map.isEmpty()) {
            return null;
        }
        Map<String, MapRoomNode> byId = new HashMap<String, MapRoomNode>();
        for (List<MapRoomNode> row : AbstractDungeon.map) {
            if (row == null) continue;
            for (MapRoomNode node : row) {
                if (node == null) continue;
                // Dense grid: keep path nodes and any edge endpoints they reference.
                if (node.hasEdges() || node.getRoom() != null) {
                    byId.put(StsMapTopology.nodeId(node.x, node.y), node);
                }
            }
        }
        // Expand with edge destinations so floor N→N+1 edges are not stripped.
        boolean grew;
        do {
            grew = false;
            List<MapRoomNode> seed = new ArrayList<MapRoomNode>(byId.values());
            for (MapRoomNode node : seed) {
                for (MapEdge edge : node.getEdges()) {
                    String destId = StsMapTopology.nodeId(edge.dstX, edge.dstY);
                    if (byId.containsKey(destId)) continue;
                    MapRoomNode dest = findOnGrid(edge.dstX, edge.dstY);
                    if (dest != null) {
                        byId.put(destId, dest);
                        grew = true;
                    }
                }
            }
        } while (grew);

        if (byId.isEmpty()) return null;

        Set<String> knownIds = new HashSet<String>(byId.keySet());
        List<MapNode> nodes = new ArrayList<MapNode>();
        for (MapRoomNode node : byId.values()) {
            nodes.add(captureNode(node, knownIds));
        }

        MapRoomNode start = AbstractDungeon.getCurrMapNode();
        String startId;
        if (start != null) {
            startId = StsMapTopology.nodeId(start.x, start.y);
            if (!contains(nodes, startId)) {
                nodes.add(captureNode(start, knownIds));
                knownIds.add(startId);
            }
            nodes = ensureStartHasOutgoing(nodes, startId);
        } else {
            startId = "virtual:start";
            nodes.add(new MapNode(startId, -2, -2, "start", "", false, entryNodes(nodes, startId)));
        }
        // After first room enter, party pins from combat node; repair empty mid-path outgoings.
        nodes = repairEmptyOutgoing(nodes, startId);

        String actId = AbstractDungeon.id;
        if (empty(actId)) actId = "Exordium";
        try {
            MapDefinition definition = new MapDefinition(mapInstanceId, actId, revision,
                digest != null ? digest : "", startId, nodes);
            BaseMod.logger.info("MapHost capture ok nodes=" + definition.nodes().size()
                + " start=" + startId + " out="
                + definition.getNode(startId).outgoingNodeIds.size() + " act=" + actId
                + " sample=" + sampleOutgoing(definition));
            return definition;
        } catch (IllegalArgumentException e) {
            BaseMod.logger.error("MapHost capture rejected topology: " + e.getMessage()
                + " nodes=" + nodes.size() + " start=" + startId + " act=" + actId);
            return null;
        }
    }

    private static MapRoomNode findOnGrid(int x, int y) {
        if (AbstractDungeon.map == null) return null;
        for (List<MapRoomNode> row : AbstractDungeon.map) {
            if (row == null) continue;
            for (MapRoomNode node : row) {
                if (node != null && node.x == x && node.y == y) return node;
            }
        }
        return null;
    }

    private static String sampleOutgoing(MapDefinition definition) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (MapNode node : definition.nodes()) {
            if (node.outgoingNodeIds.isEmpty()) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(node.nodeId).append("->").append(node.outgoingNodeIds);
            if (++n >= 3) break;
        }
        return sb.toString();
    }

    /** If the start node has no playable outgoing edges, link it to zero-in-degree path nodes. */
    private static List<MapNode> ensureStartHasOutgoing(List<MapNode> nodes, String startId) {
        MapNode start = findNode(nodes, startId);
        if (start == null || !start.outgoingNodeIds.isEmpty()) return nodes;
        List<String> entries = entryNodes(nodes, startId);
        if (entries.isEmpty()) return nodes;
        return replaceOutgoing(nodes, startId, entries);
    }

    /**
     * Path nodes that lost all edges during filtering (or never had destinations in-grid)
     * get synthetic forward edges to next-row candidates so post-combat room_pin works.
     */
    static List<MapNode> repairEmptyOutgoing(List<MapNode> nodes, String startId) {
        if (nodes == null || nodes.isEmpty()) return nodes;
        Set<String> destinations = new HashSet<String>();
        for (MapNode node : nodes) destinations.addAll(node.outgoingNodeIds);

        List<MapNode> rebuilt = new ArrayList<MapNode>();
        boolean changed = false;
        for (MapNode node : nodes) {
            if (node.outgoingNodeIds.isEmpty()
                && !"boss".equals(node.roomType)
                && !"start".equals(node.roomType)
                && !node.nodeId.equals(startId)) {
                List<String> forward = forwardCandidates(nodes, node);
                if (!forward.isEmpty()) {
                    rebuilt.add(new MapNode(node.nodeId, node.x, node.y, node.roomType, node.icon,
                        node.burningElite, forward));
                    changed = true;
                    continue;
                }
            }
            rebuilt.add(node);
        }
        return changed ? rebuilt : nodes;
    }

    /**
     * Prefer nodes at y+1; if none, zero-in-degree nodes with y greater than current.
     */
    static List<String> forwardCandidates(List<MapNode> nodes, MapNode from) {
        List<String> nextRow = new ArrayList<String>();
        List<String> later = new ArrayList<String>();
        Set<String> destinations = new HashSet<String>();
        for (MapNode node : nodes) destinations.addAll(node.outgoingNodeIds);
        for (MapNode node : nodes) {
            if (node.nodeId.equals(from.nodeId)) continue;
            if ("start".equals(node.roomType)) continue;
            if (node.y == from.y + 1) {
                nextRow.add(node.nodeId);
            } else if (node.y > from.y && !destinations.contains(node.nodeId)) {
                later.add(node.nodeId);
            }
        }
        Collections.sort(nextRow);
        if (!nextRow.isEmpty()) return nextRow;
        Collections.sort(later);
        return later;
    }

    private static List<MapNode> replaceOutgoing(List<MapNode> nodes, String nodeId, List<String> outgoing) {
        List<MapNode> rebuilt = new ArrayList<MapNode>();
        for (MapNode node : nodes) {
            if (node.nodeId.equals(nodeId)) {
                rebuilt.add(new MapNode(node.nodeId, node.x, node.y, node.roomType, node.icon,
                    node.burningElite, outgoing));
            } else {
                rebuilt.add(node);
            }
        }
        return rebuilt;
    }

    private static MapNode findNode(List<MapNode> nodes, String nodeId) {
        for (MapNode node : nodes) {
            if (node.nodeId.equals(nodeId)) return node;
        }
        return null;
    }

    private static MapNode captureNode(MapRoomNode node, Set<String> knownIds) {
        List<String> outgoing = new ArrayList<String>();
        for (MapEdge edge : node.getEdges()) {
            String dest = StsMapTopology.nodeId(edge.dstX, edge.dstY);
            // Boss sits outside the grid and is not a registered map row; defer boss entry to later slices.
            if (knownIds.contains(dest) && !outgoing.contains(dest)) outgoing.add(dest);
        }
        Collections.sort(outgoing);
        AbstractRoom room = node.getRoom();
        return new MapNode(StsMapTopology.nodeId(node.x, node.y), node.x, node.y,
            roomType(room), safeSymbol(node), node.hasEmeraldKey, outgoing);
    }

    private static String safeSymbol(MapRoomNode node) {
        try {
            String symbol = node.getRoomSymbol(Boolean.FALSE);
            return symbol != null ? symbol : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    static String roomType(AbstractRoom room) {
        if (room instanceof MonsterRoomBoss) return "boss";
        if (room instanceof MonsterRoomElite) return "elite";
        if (room instanceof MonsterRoom) return "monster";
        if (room instanceof EventRoom) return "event";
        if (room instanceof ShopRoom) return "shop";
        if (room instanceof RestRoom) return "rest";
        if (room instanceof TreasureRoom) return "treasure";
        return "unknown";
    }

    private static boolean contains(List<MapNode> nodes, String nodeId) {
        for (MapNode node : nodes) if (node.nodeId.equals(nodeId)) return true;
        return false;
    }

    /** Vanilla leaves currMapNode unset while the player is choosing the first map room. */
    private static List<String> entryNodes(List<MapNode> nodes, String excludeId) {
        List<String> entries = new ArrayList<String>();
        Set<String> destinations = new HashSet<String>();
        for (MapNode node : nodes) destinations.addAll(node.outgoingNodeIds);
        for (MapNode node : nodes) {
            if (node.nodeId.equals(excludeId)) continue;
            if (!destinations.contains(node.nodeId) && !"start".equals(node.roomType)) {
                entries.add(node.nodeId);
            }
        }
        Collections.sort(entries);
        return entries;
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
