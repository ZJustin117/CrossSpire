package crossspire.reference;

import crossspire.CrossSpireMod;
import crossspire.network.Protocol;

public class NullReference<T> extends Reference<T> {

    public NullReference(String refId, String ownerId, String resourceHash) {
        super(refId, ownerId, Type.NULL, resourceHash);
    }

    @Override
    public void dereference(Object... args) {
        basemod.BaseMod.logger.info("NullReference: cannot dereference " + refId + " (owner unavailable)");
        if (!tryDegrade() && !tryMigrate()) {
            basemod.BaseMod.logger.info("NullReference: dereference failed for " + refId);
        }
    }

    @Override
    public boolean tryDegrade() {
        if (ContentValidator.matches(resourceType(), resourceId(), resourceHash)) {
            basemod.BaseMod.logger.info("NullReference degraded to LOCAL: " + refId);
            LocalReference<Object> local = new LocalReference<Object>(resourceId(), ownerId);
            local.dereference();
            return true;
        }
        return false;
    }

    @Override
    public boolean tryMigrate() {
        basemod.BaseMod.logger.info("NullReference migration attempt for " + refId);
        if (CrossSpireMod.isConnected()) {
            Protocol.ReferenceMigrateMessage msg = new Protocol.ReferenceMigrateMessage();
            msg.source = CrossSpireMod.playerId;
            msg.seq = (int) (System.currentTimeMillis() % 100000);
            msg.refId = refId;
            msg.resourceType = resourceType();
            msg.resourceId = resourceId();
            msg.resourceHash = resourceHash;
            CrossSpireMod.send(Protocol.GSON.toJson(msg));
            basemod.BaseMod.logger.info("NullReference broadcast reference_migrate: " + refId);
        }
        return false;
    }

    private String resourceType() {
        return refId.split(":")[0];
    }

    private String resourceId() {
        String part = refId.split(":")[1];
        int at = part.indexOf('@');
        return at > 0 ? part.substring(0, at) : part;
    }
}
