package crossspire.combat;

/**
 * Buff/power instance metadata attached to a host entity.
 * Logic owner = applier-first (see ARCHITECTURE §8).
 */
public final class ComponentAttachment {

    public final String instanceId;
    public final String resourceId;
    public final String resourceHash;
    public final String logicOwnerId;
    /** player:&lt;id&gt; | monster:&lt;instance_id&gt; */
    public final String hostEntityId;
    public final int amount;
    public final String powerId;

    public ComponentAttachment(
            String instanceId,
            String resourceId,
            String resourceHash,
            String logicOwnerId,
            String hostEntityId,
            int amount,
            String powerId) {
        this.instanceId = instanceId;
        this.resourceId = resourceId;
        this.resourceHash = resourceHash;
        this.logicOwnerId = logicOwnerId;
        this.hostEntityId = hostEntityId;
        this.amount = amount;
        this.powerId = powerId;
    }

    public static ComponentAttachment ofPower(
            String instanceId,
            String powerId,
            String logicOwnerId,
            String hostEntityId,
            int amount) {
        return new ComponentAttachment(
            instanceId,
            powerId,
            null,
            logicOwnerId,
            hostEntityId,
            amount,
            powerId);
    }
}
