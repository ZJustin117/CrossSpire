package crossspire.sync;

import crossspire.CrossSpireMod;
import crossspire.network.Protocol;

public final class QueueSubmitBuilder {

    private QueueSubmitBuilder() {}

    public static Protocol.QueueSubmitMessage build(String cardId, String gameTarget) {
        Protocol.QueueSubmitMessage pkt = new Protocol.QueueSubmitMessage();
        pkt.senderId = CrossSpireMod.playerId;
        pkt.ownerId = CrossSpireMod.playerId;
        pkt.cardId = cardId;
        pkt.resourceHash = "";
        pkt.gameTarget = gameTarget != null ? gameTarget : "self";
        pkt.timestamp = System.currentTimeMillis();
        pkt.source = CrossSpireMod.playerId;
        pkt.seq = CrossSpireMod.nextSeq();
        pkt.target = CrossSpireMod.hostId;
        return pkt;
    }
}
