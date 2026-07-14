package crossspire.combat;

import basemod.BaseMod;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;
import crossspire.network.Protocol;
import crossspire.reference.Reference;
import crossspire.reference.ReferenceFactory;
import crossspire.ui.QueueDisplay;
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

        Reference<Object> ref = ReferenceFactory.createCardRef(head.cardId, head.ownerId, head.resourceHash);
        BaseMod.logger.info("QueueManager checkHead: " + head.cardId + " refType=" + ref.type + " owner=" + head.ownerId.substring(0, 8));

        if (ref.type == Reference.Type.LOCAL) {
            executing = true;
            String pid = head.packetId;
            BaseMod.logger.info("QueueManager LOCAL dereference: " + head.cardId + " (" + pid + ")");

            synchronized (packets) {
                for (int i = 0; i < packets.size(); i++) {
                    if (packets.get(i).packetId.equals(pid)) {
                        packets.remove(i);
                        break;
                    }
                }
            }

            QueueDisplay.setExecuting(pid);
            ref.dereference(head.target);
            QueueDisplay.onComplete(pid);

            executing = false;
            checkHead();

        } else if (ref.type == Reference.Type.REMOTE) {
            executing = true;
            String pid = head.packetId;
            BaseMod.logger.info("QueueManager REMOTE dereference: " + head.cardId + " -> " + head.ownerId.substring(0, 8));

            synchronized (packets) {
                for (int i = 0; i < packets.size(); i++) {
                    if (packets.get(i).packetId.equals(pid)) {
                        packets.remove(i);
                        break;
                    }
                }
            }

            QueueDisplay.setExecuting(pid);
            ref.dereference(head.target);

            executing = false;
            checkHead();

        } else {
            BaseMod.logger.info("QueueManager NULL reference for " + head.cardId + " — dequeuing");
            synchronized (packets) {
                for (int i = 0; i < packets.size(); i++) {
                    if (packets.get(i).packetId.equals(head.packetId)) {
                        packets.remove(i);
                        break;
                    }
                }
            }
        }
    }

    public void enqueueOwnCard(String cardId, String target) {
        Protocol.QueuePacket pkt = new Protocol.QueuePacket();
        pkt.packetId = CrossSpireMod.playerId + "/" + UUID.randomUUID().toString().substring(0, 8);
        pkt.senderId = CrossSpireMod.playerId;
        pkt.ownerId = CrossSpireMod.playerId;
        pkt.timestamp = System.currentTimeMillis();
        pkt.cardId = cardId;
        pkt.resourceHash = crossspire.reference.ContentValidator.hashResource("card", cardId);
        pkt.target = target;
        pkt.source = CrossSpireMod.playerId;
        pkt.seq = CrossSpireMod.nextSeq();

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
