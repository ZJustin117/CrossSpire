package crossspire.map;

import crossspire.CrossSpireMod;
import crossspire.network.PacketOperation;
import crossspire.network.Protocol;
import crossspire.network.StandardPacket;

/** Builds NIH generation commit and RoomHost party open packets. */
public final class NodeInstanceOpenedSender {

    private NodeInstanceOpenedSender() {}

    public static StandardPacket buildCommit(Protocol.NodeInstanceInfo instance,
                                             Protocol.NodeGenerationResult result,
                                             int generationRevision) {
        Protocol.NodeGenerationCommitPayload payload = new Protocol.NodeGenerationCommitPayload();
        payload.nodeInstanceId = instance.nodeInstanceId;
        payload.partyId = instance.partyId;
        payload.mapInstanceId = instance.mapInstanceId;
        payload.nodeId = instance.nodeId;
        payload.generationRevision = generationRevision;
        payload.generationResult = result;
        return packet(PacketOperation.NODE_GENERATION_COMMIT, payload, instance.nodeInstanceHostId);
    }

    public static StandardPacket buildOpened(Protocol.NodeInstanceInfo instance,
                                             Protocol.NodeGenerationResult result) {
        Protocol.NodeInstanceOpenedPayload payload = new Protocol.NodeInstanceOpenedPayload();
        Protocol.NodeInstanceInfo opened = new Protocol.NodeInstanceInfo();
        opened.nodeInstanceId = instance.nodeInstanceId;
        opened.mapInstanceId = instance.mapInstanceId;
        opened.partyId = instance.partyId;
        opened.nodeId = instance.nodeId;
        opened.visitId = instance.visitId;
        opened.nodeInstanceHostId = instance.nodeInstanceHostId;
        opened.status = "active";
        opened.generationRevision = instance.generationRevision;
        opened.stateRevision = instance.stateRevision;
        payload.nodeInstance = opened;
        payload.generationResult = result;
        return packet(PacketOperation.NODE_INSTANCE_OPENED, payload, null);
    }

    public static Protocol.NodeGenerationResult defaultMonsterResult(String nodeId) {
        Protocol.NodeGenerationResult result = new Protocol.NodeGenerationResult();
        result.roomType = "monster";
        result.encounter = "Cultist";
        result.nodeId = nodeId;
        return result;
    }

    private static StandardPacket packet(String operation, Object payload, String ownerId) {
        int seq = CrossSpireMod.nextSeq();
        StandardPacket packet = new StandardPacket();
        packet.packetId = CrossSpireMod.playerId + "-" + seq;
        packet.source = CrossSpireMod.playerId;
        packet.seq = seq;
        packet.timestamp = System.currentTimeMillis();
        packet.ownerId = ownerId;
        packet.operation = operation;
        packet.payload = Protocol.GSON.toJsonTree(payload).getAsJsonObject();
        return packet;
    }
}
