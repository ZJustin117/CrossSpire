package crossspire.resource;

import com.badlogic.gdx.graphics.Texture;

/**
 * Pure material projection for a remote potion texture and metadata.
 * Does not extend AbstractPotion — no side effects, no potion pool pollution.
 * Currently unused in render pipeline; reserved for potion art asset system.
 * Note: public mutable fields (no constructor), style inconsistency with siblings.
 */
public class RemotePotionResource {

    public String potionId;
    public String name;
    public String description;
    public int cost;

    public Texture icon;
    public boolean hasIcon;
}
