package crossspire.event;

import crossspire.network.PacketOperation;
import crossspire.network.Protocol;
import crossspire.network.StandardPacket;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class EventInterfaceFactoryTest {

    @Test
    public void buildsPartyScopedStandardInterfaceForOpenedEventNode() {
        Protocol.NodeInstanceInfo instance = new Protocol.NodeInstanceInfo();
        instance.nodeInstanceId = "node:M1/P0/event/1";
        instance.partyId = "P0";
        instance.nodeId = "event";

        Protocol.EventInterfacePayload iface = EventInterfaceFactory.create(instance, "hash-a");
        StandardPacket packet = EventChoiceSender.interfacePacket(iface);

        assertEquals(instance.nodeInstanceId + ":event", iface.eventInstanceId);
        assertEquals("P0", iface.partyId);
        assertEquals("com.megacrit.cardcrawl.events.exordium.BigFish", iface.eventClass);
        assertEquals("hash-a", iface.resourceHash);
        assertEquals(EventApprovalCoordinator.MODE_INDIVIDUAL, iface.mode);
        assertEquals(3, iface.options.length);
        assertEquals(PacketOperation.EVENT_INTERFACE, packet.operation);
        assertNotNull(packet.payload.get("event_instance_id"));
    }
}
