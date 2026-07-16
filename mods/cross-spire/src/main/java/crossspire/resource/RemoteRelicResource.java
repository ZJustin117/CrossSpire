package crossspire.resource;

import com.badlogic.gdx.graphics.Texture;

/**
 * Pure material projection for a remote relic texture and metadata.
 * Does not extend AbstractRelic — no side effects, no relic pool pollution.
 * Currently unused in render pipeline; reserved for relic art asset system.
 */
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
