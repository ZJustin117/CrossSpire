package crossspire.resource;

import com.badlogic.gdx.graphics.Texture;

public class RemoteRelicResource {

    public final String relicId;
    public final String relicName;
    public String description;
    public int counter;
    public Texture texture;

    public RemoteRelicResource(String relicId, String relicName) {
        this.relicId = relicId;
        this.relicName = relicName;
    }
}
