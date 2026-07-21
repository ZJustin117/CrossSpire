package crossspire.map;

import crossspire.CrossSpireMod;
import crossspire.network.PacketOperation;
import crossspire.network.Protocol;
import crossspire.network.StandardPacket;

/** Builds party-scoped NodeInstanceHost election packets. */
public final class NodeInstanceHostVoteSender {

    private NodeInstanceHostVoteSender() {}

    public static StandardPacket buildVote(String partyId, String candidateId) {
        Protocol.NodeInstanceHostVotePayload payload = new Protocol.NodeInstanceHostVotePayload();
        payload.partyId = partyId;
        payload.candidateId = candidateId;
        payload.partyRevision = 0;
        return packet(PacketOperation.NODE_INSTANCE_HOST_VOTE, payload);
    }

    public static StandardPacket buildResult(String partyId, String nodeInstanceHostId) {
        Protocol.NodeInstanceHostVotePayload payload = new Protocol.NodeInstanceHostVotePayload();
        payload.partyId = partyId;
        payload.nodeInstanceHostId = nodeInstanceHostId;
        payload.candidateId = nodeInstanceHostId;
        payload.partyRevision = 0;
        return packet(PacketOperation.NODE_INSTANCE_HOST_RESULT, payload);
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
