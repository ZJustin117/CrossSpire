package crossspire.network;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public final class Protocol {

    public static final Gson GSON = new Gson();

    public static class GameMessage {
        public String type;
        public String subtype;
        public String source;
        public int seq;
        public String target;
    }

    // -- queue messages --

    public static class QueueSubmit extends GameMessage {
        public QueueSubmit() { type = "queue_submit"; }
        @SerializedName("card_id") public String cardId;
        public boolean upgraded;
        @SerializedName("energy_cost") public int energyCost;
    }

    public static class QueueEntry {
        @SerializedName("entry_id") public String entryId;
        public String source;
        @SerializedName("card_id") public String cardId;
        public boolean upgraded;
        public String target;
        public String status;
    }

    public static class QueueUpdate extends GameMessage {
        public QueueUpdate() { type = "queue_update"; }
        public QueueEntry[] entries;
    }

    // -- invoke messages --

    public static class InvokeCard extends GameMessage {
        public InvokeCard() { type = "invoke_card"; }
        @SerializedName("request_id") public String requestId;
        @SerializedName("card_id") public String cardId;
        public boolean upgraded;
        @SerializedName("game_target") public String gameTarget;
    }

    public static class EffectDescription {
        public String kind;
        public String target;
        public int amount;
        @SerializedName("card_id") public String cardId;
        @SerializedName("relic_id") public String relicId;
        @SerializedName("potion_id") public String potionId;
        @SerializedName("power_id") public String powerId;
        @SerializedName("damage_type") public String damageType;
    }

    public static class OperationStep {
        public String step;
        @SerializedName("card_id") public String cardId;
        public String source;
        public String target;
        public int amount;
    }

    public static class InvokeResult extends GameMessage {
        public InvokeResult() { type = "invoke_result"; }
        @SerializedName("request_id") public String requestId;
        public EffectDescription[] effects;
        @SerializedName("operation_sequence") public OperationStep[] operationSequence;
    }

    // -- sync messages (replaces old StateSync) --

    public static class RemotePlayerState {
        public int hp;
        @SerializedName("max_hp") public int maxHp;
        public int block;
        public String[] relics;
        public String[] potions;
    }

    public static class RemotePlayerSync extends GameMessage {
        public RemotePlayerSync() { type = "state_sync"; subtype = "remote_player"; }
        public RemotePlayerState player;
    }

    private Protocol() {}
}
