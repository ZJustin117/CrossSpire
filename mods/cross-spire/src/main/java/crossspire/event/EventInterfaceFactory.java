package crossspire.event;

import crossspire.network.Protocol;
import crossspire.reference.ContentValidator;

/** Builds the deterministic built-in event descriptor used by the first event-node slice. */
public final class EventInterfaceFactory {

    private static final String BIG_FISH_CLASS = "com.megacrit.cardcrawl.events.exordium.BigFish";

    private EventInterfaceFactory() {}

    public static Protocol.EventInterfacePayload create(Protocol.NodeInstanceInfo instance) {
        return create(instance, ContentValidator.hashClass(BIG_FISH_CLASS));
    }

    static Protocol.EventInterfacePayload create(Protocol.NodeInstanceInfo instance, String resourceHash) {
        if (instance == null || empty(instance.nodeInstanceId) || empty(instance.partyId)) return null;
        Protocol.EventInterfacePayload iface = new Protocol.EventInterfacePayload();
        iface.eventInstanceId = instance.nodeInstanceId + ":event";
        iface.partyId = instance.partyId;
        iface.eventClass = BIG_FISH_CLASS;
        iface.eventId = "BigFish";
        iface.resourceHash = resourceHash != null ? resourceHash : "";
        iface.name = "Big Fish";
        iface.description = "You come across a strange creature in the road.";
        iface.mode = EventApprovalCoordinator.MODE_INDIVIDUAL;
        iface.options = new Protocol.EventOptionInfo[] {
            option(0, "Eat", true),
            option(1, "Fight", true),
            option(2, "Leave", true)
        };
        return iface;
    }

    private static Protocol.EventOptionInfo option(int index, String text, boolean enabled) {
        Protocol.EventOptionInfo option = new Protocol.EventOptionInfo();
        option.index = index;
        option.text = text;
        option.enabled = enabled;
        option.disabled = !enabled;
        return option;
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
