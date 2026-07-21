package crossspire.event;

import crossspire.CrossSpireMod;
import crossspire.network.PacketOperation;
import crossspire.network.Protocol;
import crossspire.network.StandardPacket;

/** Builds T7.4 standard packets for event approval. */
public final class EventChoiceSender {

    private EventChoiceSender() {}

    public static StandardPacket interfacePacket(Protocol.EventInterfacePayload payload) {
        return packet(PacketOperation.EVENT_INTERFACE, payload);
    }

    public static StandardPacket requestPacket(Protocol.EventChoiceRequestPayload payload) {
        return packet(PacketOperation.EVENT_CHOICE_REQUEST, payload);
    }

    public static StandardPacket approvedPacket(Protocol.EventChoiceDecisionPayload payload) {
        return packet(PacketOperation.EVENT_CHOICE_APPROVED, payload);
    }

    public static StandardPacket rejectedPacket(Protocol.EventChoiceDecisionPayload payload) {
        return packet(PacketOperation.EVENT_CHOICE_REJECTED, payload);
    }

    public static StandardPacket resultPacket(Protocol.EventPlayerResultPayload payload) {
        return packet(PacketOperation.EVENT_PLAYER_RESULT, payload);
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
