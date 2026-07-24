package crossspire.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T7.5c: when multiple parties visit the same map node_id, record the co-presence only.
 * Does not merge combat, inventories, or node instances.
 */
public final class CrossPartyNodeEncounterRecorder {

    private final Map<String, List<NodeInstance>> byMapNode =
        new LinkedHashMap<String, List<NodeInstance>>();
    private final List<CrossPartyNodeEncounter> encounters =
        new ArrayList<CrossPartyNodeEncounter>();

    public synchronized List<CrossPartyNodeEncounter> noteAllocation(NodeInstance allocated) {
        if (allocated == null) return Collections.emptyList();
        String key = mapNodeKey(allocated.mapInstanceId, allocated.nodeId);
        List<NodeInstance> others = byMapNode.get(key);
        if (others == null) {
            others = new ArrayList<NodeInstance>();
            byMapNode.put(key, others);
        }
        List<CrossPartyNodeEncounter> newEncounters = new ArrayList<CrossPartyNodeEncounter>();
        for (NodeInstance existing : others) {
            if (existing.partyId.equals(allocated.partyId)) continue;
            if (alreadyRecorded(existing, allocated)) continue;
            CrossPartyNodeEncounter encounter = new CrossPartyNodeEncounter(
                allocated.mapInstanceId, allocated.nodeId,
                existing.partyId, allocated.partyId,
                existing.nodeInstanceId, allocated.nodeInstanceId);
            encounters.add(encounter);
            newEncounters.add(encounter);
        }
        others.add(allocated);
        return newEncounters;
    }

    public synchronized List<CrossPartyNodeEncounter> snapshot() {
        return Collections.unmodifiableList(new ArrayList<CrossPartyNodeEncounter>(encounters));
    }

    public synchronized int size() {
        return encounters.size();
    }

    private boolean alreadyRecorded(NodeInstance a, NodeInstance b) {
        for (CrossPartyNodeEncounter e : encounters) {
            if (!e.mapInstanceId.equals(a.mapInstanceId) || !e.nodeId.equals(a.nodeId)) continue;
            boolean pair = (e.partyA.equals(a.partyId) && e.partyB.equals(b.partyId))
                || (e.partyA.equals(b.partyId) && e.partyB.equals(a.partyId));
            if (pair) return true;
        }
        return false;
    }

    private static String mapNodeKey(String mapInstanceId, String nodeId) {
        return mapInstanceId + "/" + nodeId;
    }
}
