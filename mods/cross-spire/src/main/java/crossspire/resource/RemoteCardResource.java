package crossspire.resource;

import com.badlogic.gdx.graphics.Texture;

/**
 * Pure material projection for a remote card texture.
 * Does not extend AbstractCard — no side effects, no card pool pollution.
 * Currently unused in render pipeline; reserved for card art asset system.
 */
public class RemoteCardResource {

    public final String cardId;
    public final String cardName;
    public String description;
    public int energyCost;
    public String type;
    public String rarity;
    public Texture largeTexture;
    public Texture smallTexture;

    public RemoteCardResource(String cardId, String cardName) {
        this.cardId = cardId;
        this.cardName = cardName;
    }

    public boolean hasLargeTexture() { return largeTexture != null; }
    public boolean hasSmallTexture() { return smallTexture != null; }
}
