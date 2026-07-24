package crossspire.map;

import crossspire.CrossSpireMod;
import crossspire.network.PacketOperation;
import crossspire.network.Protocol;
import crossspire.network.StandardPacket;
import java.util.List;

/** Builds MapHost registration and RoomHost acceptance packets. */
public final class MapRegisterSender {

    private MapRegisterSender() {}

    public static StandardPacket buildRegister(String partyId, String mapHostId, String requestId,
                                               MapDefinition map) {
        Protocol.MapRegistrationPayload payload = new Protocol.MapRegistrationPayload();
        payload.partyId = partyId;
        payload.mapHostId = mapHostId;
        payload.requestId = requestId;
        payload.map = toProtocol(map);
        return packet(PacketOperation.MAP_REGISTER, payload);
    }

    public static StandardPacket buildRegistered(String partyId, MapDefinition map) {
        Protocol.MapRegisteredPayload payload = new Protocol.MapRegisteredPayload();
        payload.partyId = partyId;
        payload.mapInstanceId = map.mapInstanceId;
        payload.startNodeId = map.startNodeId;
        payload.mapRevision = map.mapRevision;
        payload.partyRevision = 0;
        payload.map = toProtocol(map);
        return packet(PacketOperation.MAP_REGISTERED, payload);
    }

    public static Protocol.MapDefinition toProtocol(MapDefinition map) {
        Protocol.MapDefinition dto = new Protocol.MapDefinition();
        dto.mapInstanceId = map.mapInstanceId;
        dto.actId = map.actId;
        dto.mapRevision = map.mapRevision;
        dto.generationDigest = map.generationDigest;
        dto.startNodeId = map.startNodeId;
        List<MapNode> nodes = map.nodes();
        dto.nodes = new Protocol.MapNode[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            MapNode node = nodes.get(i);
            Protocol.MapNode n = new Protocol.MapNode();
            n.nodeId = node.nodeId;
            n.x = node.x;
            n.y = node.y;
            n.roomType = node.roomType;
            n.icon = node.icon;
            n.burningElite = node.burningElite;
            n.outgoingNodeIds = node.outgoingNodeIds.toArray(new String[0]);
            dto.nodes[i] = n;
        }
        return dto;
    }

    private static StandardPacket packet(String operation, Object payload) {
        int seq = CrossSpireMod.nextSeq();
        StandardPacket packet = new StandardPacket();
        packet.packetId = CrossSpireMod.playerId + "-" + seq;
        packet.source = CrossSpireMod.playerId;
        packet.seq = seq;
        packet.timestamp = System.currentTimeMillis();
        packet.operation = operation;
        packet.payload = Protocol.GSON.toJsonTree(payload).getAsJsonObject();
        return packet;
    }
}
