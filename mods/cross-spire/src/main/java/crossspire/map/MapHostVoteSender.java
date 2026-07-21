package crossspire.map;

import crossspire.CrossSpireMod;
import crossspire.network.PacketOperation;
import crossspire.network.Protocol;
import crossspire.network.StandardPacket;

/** Builds party-scoped MapHost election packets. */
public final class MapHostVoteSender {

    private MapHostVoteSender() {}

    public static StandardPacket buildVote(String partyId, String candidateId) {
        Protocol.MapHostVotePayload payload = new Protocol.MapHostVotePayload();
        payload.partyId = partyId;
        payload.candidateId = candidateId;
        payload.partyRevision = 0;
        return packet(PacketOperation.MAP_HOST_VOTE, payload);
    }

    public static StandardPacket buildResult(String partyId, String mapHostId) {
        Protocol.MapHostVotePayload payload = new Protocol.MapHostVotePayload();
        payload.partyId = partyId;
        payload.mapHostId = mapHostId;
        payload.candidateId = mapHostId;
        payload.partyRevision = 0;
        return packet(PacketOperation.MAP_HOST_RESULT, payload);
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
