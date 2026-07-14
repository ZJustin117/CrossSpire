package crossspire.reference;

import crossspire.CrossSpireMod;

public abstract class Reference<T> {

    public enum Type { LOCAL, REMOTE, NULL }

    public final String refId;
    public final String ownerId;
    public final String hostId;
    public final Type type;
    protected String resourceHash;

    protected Reference(String refId, String ownerId, Type type, String resourceHash) {
        this.refId = refId;
        this.ownerId = ownerId;
        this.hostId = CrossSpireMod.hostId;
        this.type = type;
        this.resourceHash = resourceHash;
    }

    public abstract void dereference(Object... args);

    public boolean tryDegrade() { return false; }
    public boolean tryMigrate() { return false; }

    public Reference<T> triggerOn(String eventType) {
        TriggerRegistry.register(eventType, refId, this);
        return this;
    }
}
