package crossspire.party;

import crossspire.network.Protocol;

import java.util.ArrayList;
import java.util.List;

/** Converts pure party directory state to and from the protocol payload. */
public final class PartySnapshotCodec {

    private PartySnapshotCodec() {}

    public static Protocol.PartySnapshotPayload toPayload(List<PartyState> parties) {
        Protocol.PartySnapshotPayload payload = new Protocol.PartySnapshotPayload();
        payload.parties = new Protocol.PartyInfo[parties.size()];
        for (int i = 0; i < parties.size(); i++) {
            PartyState party = parties.get(i);
            Protocol.PartyInfo info = new Protocol.PartyInfo();
            info.partyId = party.partyId;
            info.leaderId = party.leaderId;
            info.memberIds = party.memberIds.toArray(new String[0]);
            info.mapPosition = party.mapPosition;
            info.phaseStatus = party.phaseStatus;
            info.actId = party.actId;
            info.mapInstanceId = party.mapInstanceId;
            info.nodeInstanceHostId = party.nodeInstanceHostId;
            info.activeNodeInstanceId = party.activeNodeInstanceId;
            info.partyRevision = party.partyRevision;
            payload.parties[i] = info;
        }
        return payload;
    }

    public static boolean apply(PartyManager manager, Protocol.PartySnapshotPayload payload) {
        if (manager == null || payload == null || payload.parties == null) return false;
        List<PartyState> parties = new ArrayList<PartyState>();
        for (Protocol.PartyInfo info : payload.parties) {
            if (info == null || info.partyId == null || info.leaderId == null || info.memberIds == null) {
                return false;
            }
            PartyState party = new PartyState(info.partyId,
                java.util.Arrays.asList(info.memberIds), info.mapPosition,
                info.phaseStatus, info.actId, info.mapInstanceId,
                info.nodeInstanceHostId, info.activeNodeInstanceId, info.partyRevision);
            if (!info.leaderId.equals(party.leaderId)) return false;
            parties.add(party);
        }
        return manager.replaceSnapshot(parties);
    }
}
