package crossspire.resource;

import com.badlogic.gdx.graphics.Texture;

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
