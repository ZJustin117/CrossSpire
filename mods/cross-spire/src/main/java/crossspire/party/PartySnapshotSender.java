package crossspire.party;

import basemod.BaseMod;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;
import crossspire.network.PacketOperation;
import crossspire.network.Protocol;
import crossspire.network.StandardPacket;

/** Builds the RoomHost-authoritative party directory broadcast. */
public final class PartySnapshotSender {

    private PartySnapshotSender() {}

    public static StandardPacket build() {
        int seq = CrossSpireMod.nextSeq();
        StandardPacket packet = new StandardPacket();
        packet.packetId = CrossSpireMod.playerId + "-" + seq;
        packet.source = CrossSpireMod.playerId;
        packet.seq = seq;
        packet.timestamp = System.currentTimeMillis();
        packet.operation = PacketOperation.PARTY_SNAPSHOT;
        packet.payload = Protocol.GSON.toJsonTree(
            PartySnapshotCodec.toPayload(CrossSpireMod.partyManager.snapshot())).getAsJsonObject();
        BaseMod.logger.info("PartySnapshotSender broadcast " + packet.payload);
        return packet;
    }
}
