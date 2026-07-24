package crossspire.event;

import crossspire.network.Protocol;

/**
 * Pure NIH-side planner for approved fallback event choices.
 * Produces a personal event_player_result without mutating shared world state.
 */
public final class FallbackNihResultPlanner {

    private FallbackNihResultPlanner() {}

    public static Protocol.EventPlayerResultPayload plan(Protocol.EventInterfacePayload iface,
                                                           Protocol.EventChoiceDecisionPayload decision,
                                                           String chooserPlayerId) {
        if (iface == null || decision == null || empty(chooserPlayerId)
            || empty(decision.eventInstanceId) || empty(decision.requestId)
            || empty(decision.partyId)
            || !decision.eventInstanceId.equals(iface.eventInstanceId)
            || !decision.partyId.equals(iface.partyId)
            || !isEnabledOption(iface, decision.optionIndex)) {
            return null;
        }
        Protocol.EventPlayerResultPayload result = new Protocol.EventPlayerResultPayload();
        result.eventInstanceId = decision.eventInstanceId;
        result.partyId = decision.partyId;
        result.requestId = decision.requestId;
        result.playerId = chooserPlayerId;
        result.effects = effectsForOption(decision.optionIndex);
        return result;
    }

    private static boolean isEnabledOption(Protocol.EventInterfacePayload iface, int optionIndex) {
        if (iface.options == null) return false;
        for (Protocol.EventOptionInfo opt : iface.options) {
            if (opt == null || opt.index != optionIndex) continue;
            return opt.enabled || !opt.disabled;
        }
        return false;
    }

    /** Deterministic personal-only effects for diagnostic / first-pass fallback content. */
    private static Protocol.EffectDescription[] effectsForOption(int optionIndex) {
        Protocol.EffectDescription gold = new Protocol.EffectDescription();
        gold.kind = "gain_gold";
        gold.amount = 10 * (optionIndex + 1);
        gold.target = "self";
        return new Protocol.EffectDescription[] {gold};
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
