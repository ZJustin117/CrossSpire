package crossspire.reference;

import basemod.BaseMod;
import crossspire.CrossSpireMod;

public final class ReferenceFactory {

    public static Reference<Object> createCardRef(String cardId, String ownerId, String resourceHash) {
        if (ownerId == null || ownerId.isEmpty() || ownerId.equals(CrossSpireMod.playerId)) {
            return new LocalReference<Object>(cardId, ownerId);
        }

        if (CrossSpireMod.p2pManager != null && CrossSpireMod.p2pManager.hasDirectConnection(ownerId)) {
            return new RemoteReference<Object>(cardId, ownerId, resourceHash, true);
        }

        if (CrossSpireMod.isConnected()) {
            return new RemoteReference<Object>(cardId, ownerId, resourceHash, false);
        }

        return new NullReference<Object>("card:" + cardId + "@" + ownerId, ownerId, resourceHash);
    }

    private ReferenceFactory() {}
}
