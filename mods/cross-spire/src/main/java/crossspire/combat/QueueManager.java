package crossspire.combat;

import basemod.BaseMod;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;
import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class QueueManager {

    private final List<Protocol.QueuePacket> packets = Collections.synchronizedList(new ArrayList<Protocol.QueuePacket>());
    private boolean executing = false;

    private static final Comparator<Protocol.QueuePacket> ORDER = new Comparator<Protocol.QueuePacket>() {
        @Override
        public int compare(Protocol.QueuePacket a, Protocol.QueuePacket b) {
            if (a.timestamp != b.timestamp) return Long.compare(a.timestamp, b.timestamp);
            return a.senderId.compareTo(b.senderId);
        }
    };

    public void onQueuePacket(Protocol.QueuePacket pkt) {
        String raw = Protocol.GSON.toJson(pkt);
        Protocol.QueuePacket parsed = Protocol.GSON.fromJson(raw, Protocol.QueuePacket.class);
        if (parsed.timestamp == 0) parsed.timestamp = System.currentTimeMillis();

        synchronized (packets) {
            for (Protocol.QueuePacket existing : packets) {
                if (existing.packetId.equals(parsed.packetId)) return;
            }
            packets.add(parsed);
            Collections.sort(packets, ORDER);
        }

        BaseMod.logger.info("QueueManager received packet: " + parsed.cardId + " by " + parsed.senderId.substring(0,8)
            + " owner=" + parsed.ownerId.substring(0,8) + " queue_size=" + packets.size());
        checkHead();
    }

    public void onQueueComplete(Protocol.QueueComplete complete) {
        if (complete.source != null && complete.source.equals(CrossSpireMod.playerId)) return;

        synchronized (packets) {
            for (int i = 0; i < packets.size(); i++) {
                if (packets.get(i).packetId.equals(complete.packetId)) {
                    packets.remove(i);
                    BaseMod.logger.info("QueueManager queue_complete: " + complete.packetId + " remaining=" + packets.size());
                    return;
                }
            }
        }
    }

    private void checkHead() {
        if (executing) return;

        Protocol.QueuePacket head;
        synchronized (packets) {
            if (packets.isEmpty()) return;
            head = packets.get(0);
        }

        if (!head.ownerId.equals(CrossSpireMod.playerId)) return;

        executing = true;

        String pid = head.packetId;
        BaseMod.logger.info("QueueManager executing own packet: " + head.cardId + " (" + pid + ")");

        Protocol.EffectDescription dmg = new Protocol.EffectDescription();
        dmg.kind = "damage";
        dmg.target = head.target;
        dmg.amount = 6;

        EventSuppression.suppressEvents(() -> {
            BaseMod.logger.info("QueueManager executed (suppressed): " + head.cardId);
        });

        synchronized (packets) {
            for (int i = 0; i < packets.size(); i++) {
                if (packets.get(i).packetId.equals(pid)) {
                    packets.remove(i);
                    break;
                }
            }
        }

        Protocol.QueueComplete complete = new Protocol.QueueComplete();
        complete.source = CrossSpireMod.playerId;
        complete.seq = 1;
        complete.packetId = pid;
        complete.effects = new Protocol.EffectDescription[] { dmg };
        complete.operationSequence = new Protocol.OperationStep[0];

        if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            CrossSpireMod.relayClient.send(Protocol.GSON.toJson(complete));
        }

        executing = false;
        checkHead();
    }

    public void enqueueOwnCard(String cardId, String target) {
        Protocol.QueuePacket pkt = new Protocol.QueuePacket();
        pkt.packetId = CrossSpireMod.playerId + "/" + UUID.randomUUID().toString().substring(0, 8);
        pkt.senderId = CrossSpireMod.playerId;
        pkt.ownerId = CrossSpireMod.playerId;
        pkt.timestamp = System.currentTimeMillis();
        pkt.cardId = cardId;
        pkt.resourceHash = "";
        pkt.target = target;
        pkt.source = CrossSpireMod.playerId;
        pkt.seq = 1;

        if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            CrossSpireMod.relayClient.send(Protocol.GSON.toJson(pkt));
        }

        onQueuePacket(pkt);
        BaseMod.logger.info("QueueManager enqueue own: " + cardId);
    }

    public int size() {
        synchronized (packets) {
            return packets.size();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }
}
