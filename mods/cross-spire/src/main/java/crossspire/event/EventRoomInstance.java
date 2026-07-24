package crossspire.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Party-scoped shared path created when an approved event option spawns an in-event room/combat.
 * Same option index reuses the same instance; members only join after choosing that option.
 */
public final class EventRoomInstance {

    public final String instanceId;
    public final String partyId;
    public final String eventInstanceId;
    public final int optionIndex;
    public final String nodeInstanceHostId;
    private final Set<String> memberIds = new LinkedHashSet<String>();

    public EventRoomInstance(String instanceId, String partyId, String eventInstanceId,
                             int optionIndex, String nodeInstanceHostId, String firstMemberId) {
        this.instanceId = instanceId;
        this.partyId = partyId;
        this.eventInstanceId = eventInstanceId;
        this.optionIndex = optionIndex;
        this.nodeInstanceHostId = nodeInstanceHostId != null ? nodeInstanceHostId : "";
        if (firstMemberId != null && !firstMemberId.isEmpty()) {
            this.memberIds.add(firstMemberId);
        }
    }

    public synchronized boolean addMember(String playerId) {
        if (playerId == null || playerId.isEmpty()) return false;
        return memberIds.add(playerId);
    }

    public synchronized boolean contains(String playerId) {
        return playerId != null && memberIds.contains(playerId);
    }

    public synchronized List<String> members() {
        return Collections.unmodifiableList(new ArrayList<String>(memberIds));
    }

    public synchronized int size() {
        return memberIds.size();
    }
}
