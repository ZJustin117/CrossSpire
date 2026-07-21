package crossspire.map;

import crossspire.CrossSpireMod;
import crossspire.network.PacketOperation;
import crossspire.network.Protocol;
import crossspire.network.StandardPacket;

/** Builds RoomHost → NodeInstanceHost allocation packets. */
public final class NodeInstanceAllocateSender {

    private NodeInstanceAllocateSender() {}

    public static StandardPacket build(NodeInstance instance, String requestId) {
        Protocol.NodeInstanceInfo info = new Protocol.NodeInstanceInfo();
        info.nodeInstanceId = instance.nodeInstanceId;
        info.mapInstanceId = instance.mapInstanceId;
        info.partyId = instance.partyId;
        info.nodeId = instance.nodeId;
        info.visitId = instance.visitId;
        info.nodeInstanceHostId = instance.nodeInstanceHostId;
        info.status = "allocated";
        info.generationRevision = 0;
        info.stateRevision = 0;

        Protocol.NodeInstanceAllocatePayload payload = new Protocol.NodeInstanceAllocatePayload();
        payload.nodeInstance = info;
        payload.requestId = requestId != null ? requestId : "";

        int seq = CrossSpireMod.nextSeq();
        StandardPacket packet = new StandardPacket();
        packet.packetId = CrossSpireMod.playerId + "-" + seq;
        packet.source = CrossSpireMod.playerId;
        packet.seq = seq;
        packet.timestamp = System.currentTimeMillis();
        packet.ownerId = instance.nodeInstanceHostId;
        packet.operation = PacketOperation.NODE_INSTANCE_ALLOCATE;
        packet.payload = Protocol.GSON.toJsonTree(payload).getAsJsonObject();
        return packet;
    }
}
