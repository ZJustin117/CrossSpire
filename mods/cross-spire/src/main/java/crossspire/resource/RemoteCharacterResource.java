package crossspire.resource;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;

public class RemoteCharacterResource {

    public final String playerId;
    public SkeletonData skeletonData;
    public TextureAtlas atlas;
    public Skeleton skeleton;
    public AnimationState state;
    public AnimationStateData stateData;

    public float drawX;
    public float drawY;
    public float scaleX = 1.0f;
    public float scaleY = 1.0f;

    public String currentAnimation = "Idle";
    public boolean loaded;

    public RemoteCharacterResource(String playerId) {
        this.playerId = playerId;
    }

    public boolean isLoaded() {
        return loaded && skeletonData != null && atlas != null;
    }

    public void buildRenderables() {
        if (skeletonData == null || atlas == null) return;
        skeleton = new Skeleton(skeletonData);
        stateData = new AnimationStateData(skeletonData);
        state = new AnimationState(stateData);
        state.setAnimation(0, currentAnimation, true);
        loaded = true;
    }

    public void setAnimation(String animation, boolean loop) {
        currentAnimation = animation;
        if (state != null) {
            state.setAnimation(0, animation, loop);
        }
    }

    public void update(float delta) {
        if (state != null) {
            state.update(delta);
            state.apply(skeleton);
        }
        if (skeleton != null) {
            skeleton.setPosition(drawX, drawY);
            if (skeleton.getRootBone() != null) {
                skeleton.getRootBone().setScaleX(scaleX);
                skeleton.getRootBone().setScaleY(scaleY);
            }
            skeleton.updateWorldTransform();
        }
    }

    public void render(SpriteBatch sb) {
        if (!isLoaded()) return;
        try {
            com.esotericsoftware.spine.SkeletonRenderer renderer =
                com.megacrit.cardcrawl.core.AbstractCreature.sr;
            if (renderer != null) {
                renderer.draw(sb, skeleton);
            }
        } catch (Exception ignored) {
        }
    }
}
