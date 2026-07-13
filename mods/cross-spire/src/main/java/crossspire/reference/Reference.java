package crossspire.reference;

public abstract class Reference<T> {

    public enum Type { LOCAL, REMOTE, NULL }

    public final String refId;
    public final String ownerId;
    public final Type type;
    protected String resourceHash;
    public final String remoteAddr;
    public final int remotePort;

    protected Reference(String refId, String ownerId, Type type, String resourceHash) {
        this.refId = refId;
        this.ownerId = ownerId;
        this.type = type;
        this.resourceHash = resourceHash;
        this.remoteAddr = "";
        this.remotePort = 0;
    }

    public abstract void dereference(Object... args);

    public boolean tryDegrade() { return false; }
    public boolean tryMigrate() { return false; }

    public Reference<T> triggerOn(String eventType) {
        TriggerRegistry.register(eventType, refId, this);
        return this;
    }
}
