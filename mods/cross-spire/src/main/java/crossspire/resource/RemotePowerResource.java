package crossspire.resource;

import com.badlogic.gdx.graphics.Texture;

/**
 * Pure material projection for a remote power icon (48 and 84 sizes) and metadata.
 * Does not extend AbstractPower — no side effects, no power pool pollution.
 * Currently unused in render pipeline; reserved for power art asset system.
 */
public class RemotePowerResource {

    public final String powerId;
    public final String powerName;
    public String description;
    public int amount;
    public Texture icon48;
    public Texture icon84;

    public RemotePowerResource(String powerId, String powerName) {
        this.powerId = powerId;
        this.powerName = powerName;
    }
}
