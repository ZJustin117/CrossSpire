package crossspire.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import crossspire.combat.ComponentAttachment;
import crossspire.map.MapDefinition;
import crossspire.map.MapNode;
import crossspire.map.MapRegisterSender;
import crossspire.map.MapRegistry;
import crossspire.party.PartyState;
import java.util.List;

/** Pure assembly of schema-aligned full_snapshot directory fields. */
public final class FullSnapshotBuilder {

    private FullSnapshotBuilder() {}

    public static JsonObject buildDirectory(List<PartyState> parties,
                                            MapRegistry maps,
                                            List<ComponentAttachment> attachments) {
        JsonObject snap = new JsonObject();
        snap.addProperty("type", "full_snapshot");
        snap.add("parties", partiesArray(parties));
        snap.add("maps", mapsArray(maps, parties));
        snap.add("attachments", attachmentsArray(attachments));
        snap.add("active_node_instances", activeNodesArray(parties));
        return snap;
    }

    private static JsonArray partiesArray(List<PartyState> parties) {
        JsonArray arr = new JsonArray();
        if (parties == null) return arr;
        for (PartyState party : parties) {
            if (party == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("party_id", party.partyId);
            o.addProperty("leader_id", party.leaderId);
            JsonArray members = new JsonArray();
            for (String member : party.memberIds) members.add(member);
            o.add("member_ids", members);
            o.addProperty("phase_status", party.phaseStatus);
            o.addProperty("act_id", party.actId);
            o.addProperty("map_instance_id", party.mapInstanceId);
            o.addProperty("map_position", party.mapPosition);
            o.addProperty("node_instance_host_id", party.nodeInstanceHostId);
            o.addProperty("active_node_instance_id", party.activeNodeInstanceId);
            o.addProperty("party_revision", party.partyRevision);
            arr.add(o);
        }
        return arr;
    }

    private static JsonArray mapsArray(MapRegistry maps, List<PartyState> parties) {
        JsonArray arr = new JsonArray();
        if (maps == null || parties == null) return arr;
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (PartyState party : parties) {
            if (party == null || party.mapInstanceId == null || party.mapInstanceId.isEmpty()) continue;
            if (!seen.add(party.mapInstanceId)) continue;
            MapDefinition map = maps.get(party.mapInstanceId);
            if (map == null) continue;
            arr.add(MapRegisterSender.toProtocol(map) == null
                ? mapJson(map)
                : crossspire.network.Protocol.GSON.toJsonTree(MapRegisterSender.toProtocol(map)));
        }
        return arr;
    }

    private static JsonObject mapJson(MapDefinition map) {
        JsonObject o = new JsonObject();
        o.addProperty("map_instance_id", map.mapInstanceId);
        o.addProperty("act_id", map.actId);
        o.addProperty("start_node_id", map.startNodeId);
        JsonArray nodes = new JsonArray();
        for (MapNode node : map.nodes()) {
            JsonObject n = new JsonObject();
            n.addProperty("node_id", node.nodeId);
            n.addProperty("room_type", node.roomType);
            nodes.add(n);
        }
        o.add("nodes", nodes);
        return o;
    }

    private static JsonArray attachmentsArray(List<ComponentAttachment> attachments) {
        JsonArray arr = new JsonArray();
        if (attachments == null) return arr;
        for (ComponentAttachment a : attachments) {
            if (a == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("instance_id", a.instanceId);
            o.addProperty("power_id", a.powerId);
            o.addProperty("logic_owner_id", a.logicOwnerId);
            o.addProperty("host_entity_id", a.hostEntityId);
            o.addProperty("amount", a.amount);
            arr.add(o);
        }
        return arr;
    }

    private static JsonArray activeNodesArray(List<PartyState> parties) {
        JsonArray arr = new JsonArray();
        if (parties == null) return arr;
        for (PartyState party : parties) {
            if (party == null || party.activeNodeInstanceId == null
                || party.activeNodeInstanceId.isEmpty()) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("party_id", party.partyId);
            o.addProperty("map_instance_id", party.mapInstanceId);
            o.addProperty("node_instance_id", party.activeNodeInstanceId);
            o.addProperty("map_position", party.mapPosition);
            o.addProperty("node_instance_host_id", party.nodeInstanceHostId);
            arr.add(o);
        }
        return arr;
    }
}
