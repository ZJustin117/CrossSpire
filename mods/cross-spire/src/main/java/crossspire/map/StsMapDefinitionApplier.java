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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reconstructs the authoritative immutable topology before any node instance is opened. */
public final class StsMapDefinitionApplier {

    private static MapDefinition active;
    private static final Map<String, MapRoomNode> offGridNodes = new HashMap<String, MapRoomNode>();

    private StsMapDefinitionApplier() {}

    public static MapDefinition active() {
        return active;
    }

    public static boolean apply(MapDefinition definition) {
        if (definition == null) return false;
        Map<String, MapRoomNode> nodes = new HashMap<String, MapRoomNode>();
        for (MapNode definitionNode : definition.nodes()) {
            if (nodes.put(definitionNode.nodeId, createNode(definitionNode)) != null) return false;
        }
        MapRoomNode start = nodes.get(definition.startNodeId);
        if (start == null) return false;
        for (MapNode definitionNode : definition.nodes()) {
            MapRoomNode source = nodes.get(definitionNode.nodeId);
            for (String destinationId : definitionNode.outgoingNodeIds) {
                MapRoomNode destination = nodes.get(destinationId);
                if (destination == null) return false;
                source.addEdge(new MapEdge(source.x, source.y, destination.x, destination.y));
            }
        }
        // Vanilla AbstractDungeon.map is indexed by y>=0. Keep negative-y path anchors off-grid.
        List<List<MapRoomNode>> rows = positiveRows(nodes, definition);
        if (rows == null) return false;
        AbstractDungeon.map = new ArrayList<ArrayList<MapRoomNode>>();
        for (List<MapRoomNode> row : rows) {
            AbstractDungeon.map.add(new ArrayList<MapRoomNode>(row));
        }
        offGridNodes.clear();
        for (MapNode definitionNode : definition.nodes()) {
            if (definitionNode.y < 0) offGridNodes.put(definitionNode.nodeId, nodes.get(definitionNode.nodeId));
        }
        // Preserve Neow / active rooms: overwriting currMapNode with a bare start crashes TopPanel.
        MapRoomNode previous = AbstractDungeon.getCurrMapNode();
        if (previous != null && previous.room != null) {
            AbstractDungeon.currMapNode = previous;
        } else {
            // setCurrMapNode indexes map by node.y and crashes for synthetic/root y=-1.
            AbstractDungeon.currMapNode = start;
        }
        active = definition;
        BaseMod.logger.info("StsMapDefinitionApplier applied map=" + definition.mapInstanceId
            + " start=" + definition.startNodeId + " nodes=" + definition.nodes().size()
            + " rows=" + rows.size()
            + " keepCurr=" + (previous != null && previous.room != null));
        return true;
    }

    public static MapRoomNode find(String nodeId) {
        if (nodeId == null) return null;
        MapRoomNode current = AbstractDungeon.getCurrMapNode();
        if (current != null && nodeId.equals(idOf(current))) return current;
        MapRoomNode offGrid = offGridNodes.get(nodeId);
        if (offGrid != null) return offGrid;
        if (AbstractDungeon.map != null) {
            for (List<MapRoomNode> row : AbstractDungeon.map) {
                if (row == null) continue;
                for (MapRoomNode node : row) {
                    if (node != null && nodeId.equals(idOf(node))) return node;
                }
            }
        }
        return null;
    }

    /** Topology reachability from the active authoritative map, not raw STS edge objects. */
    public static boolean isReachable(String fromNodeId, String toNodeId) {
        if (active == null) return false;
        if (fromNodeId != null && fromNodeId.equals(toNodeId)) return true;
        if (active.hasEdge(fromNodeId, toNodeId)) return true;
        MapNode from = active.getNode(fromNodeId);
        return from != null && MapNavigation.forwardFallback(active, from).contains(toNodeId);
    }

    private static String idOf(MapRoomNode node) {
        if (active != null) {
            for (MapNode definitionNode : active.nodes()) {
                if (definitionNode.x == node.x && definitionNode.y == node.y) return definitionNode.nodeId;
            }
        }
        return StsMapTopology.nodeId(node.x, node.y);
    }

    private static MapRoomNode createNode(MapNode source) {
        MapRoomNode node = new MapRoomNode(source.x, source.y);
        AbstractRoom room = room(source.roomType);
        if (room != null) node.setRoom(room);
        node.hasEmeraldKey = source.burningElite;
        return node;
    }

    private static AbstractRoom room(String type) {
        if ("monster".equals(type)) return new MonsterRoom();
        if ("event".equals(type)) return new EventRoom();
        if ("elite".equals(type)) return new MonsterRoomElite();
        if ("boss".equals(type)) return new MonsterRoomBoss();
        if ("shop".equals(type)) return new ShopRoom();
        if ("rest".equals(type)) return new RestRoom();
        if ("treasure".equals(type)) return new TreasureRoom();
        return null;
    }

    private static List<List<MapRoomNode>> positiveRows(Map<String, MapRoomNode> nodes,
                                                          MapDefinition definition) {
        int maximumY = -1;
        for (MapNode node : definition.nodes()) {
            if (node.y >= 0) maximumY = Math.max(maximumY, node.y);
        }
        if (maximumY < 0) return null;
        List<List<MapRoomNode>> rows = new ArrayList<List<MapRoomNode>>();
        for (int y = 0; y <= maximumY; y++) rows.add(new ArrayList<MapRoomNode>());
        for (MapNode node : definition.nodes()) {
            if (node.y >= 0) rows.get(node.y).add(nodes.get(node.nodeId));
        }
        return rows;
    }
}
