package crossspire.event;

import crossspire.network.Protocol;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** One-shot approval state for locally instantiated native event dialogs. */
public final class NativeEventApprovalGate {

    public static final String STEP_BUTTON = "buttonEffect";
    public static final String STEP_CARD_SELECT = "cardSelect";
    public static final String STEP_TARGET_SELECT = "targetSelect";
    public static final String STEP_CONFIRM = "confirm";

    public static final class Attempt {
        public final boolean execute;
        public final Protocol.EventChoiceRequestPayload request;

        private Attempt(boolean execute, Protocol.EventChoiceRequestPayload request) {
            this.execute = execute;
            this.request = request;
        }

        public static Attempt execute() {
            return new Attempt(true, null);
        }

        public static Attempt block(Protocol.EventChoiceRequestPayload request) {
            return new Attempt(false, request);
        }
    }

    private static final class Binding {
        final Protocol.EventInterfacePayload iface;
        Protocol.EventChoiceRequestPayload pending;
        Protocol.EventChoiceDecisionPayload lastApproved;
        int lastOptionIndex;
        boolean permitNext;

        Binding(Protocol.EventInterfacePayload iface) {
            this.iface = iface;
            this.lastOptionIndex = 0;
        }
    }

    private final Map<Object, Binding> bindings = new IdentityHashMap<Object, Binding>();
    private final AtomicLong requestSequence = new AtomicLong();

    public synchronized boolean bind(Object event, Protocol.EventInterfacePayload iface) {
        if (event == null || !validInterface(iface)) return false;
        bindings.put(event, new Binding(iface));
        return true;
    }

    public synchronized Attempt beforeButtonEffect(Object event, int optionIndex) {
        return beforeChoice(event, STEP_BUTTON, optionIndex, null, null);
    }

    /**
     * Multi-step entry: buttonEffect, cardSelect, targetSelect, or confirm.
     * Each step requires its own matching approval before a single execute.
     */
    public synchronized Attempt beforeChoice(Object event, String uiStep, int optionIndex,
                                             String[] selectedCards) {
        return beforeChoice(event, uiStep, optionIndex, selectedCards, null);
    }

    public synchronized Attempt beforeChoice(Object event, String uiStep, int optionIndex,
                                             String[] selectedCards, String[] selectedTargets) {
        Binding binding = bindings.get(event);
        if (binding == null) return Attempt.execute();
        if (binding.permitNext) {
            binding.permitNext = false;
            binding.pending = null;
            return Attempt.execute();
        }
        if (empty(uiStep) || !isKnownStep(uiStep)) return Attempt.block(null);
        if (binding.pending != null) return Attempt.block(null);
        if (STEP_BUTTON.equals(uiStep) && !enabled(binding.iface, optionIndex)) {
            return Attempt.block(null);
        }
        if (STEP_CARD_SELECT.equals(uiStep)
            && (selectedCards == null || selectedCards.length == 0)) {
            return Attempt.block(null);
        }
        if (STEP_TARGET_SELECT.equals(uiStep)
            && (selectedTargets == null || selectedTargets.length == 0)) {
            return Attempt.block(null);
        }

        Protocol.EventChoiceRequestPayload request = new Protocol.EventChoiceRequestPayload();
        request.eventInstanceId = binding.iface.eventInstanceId;
        request.partyId = binding.iface.partyId;
        request.requestId = binding.iface.eventInstanceId + ":"
            + requestSequence.incrementAndGet();
        request.uiStep = uiStep;
        request.optionIndex = optionIndex;
        request.resourceHash = binding.iface.resourceHash;
        if (selectedCards != null) {
            request.selectedCards = selectedCards.clone();
        }
        if (selectedTargets != null) {
            request.selectedTargets = selectedTargets.clone();
        }
        binding.pending = request;
        return Attempt.block(request);
    }

    public synchronized boolean approve(Protocol.EventChoiceDecisionPayload decision) {
        Binding binding = bindingFor(decision);
        if (binding == null || !matches(binding.pending, decision)) return false;
        binding.permitNext = true;
        binding.lastApproved = copyDecision(decision);
        binding.lastOptionIndex = decision.optionIndex;
        return true;
    }

    public synchronized boolean reject(Protocol.EventChoiceDecisionPayload decision) {
        Binding binding = bindingFor(decision);
        if (binding == null || !matches(binding.pending, decision)) return false;
        binding.pending = null;
        binding.permitNext = false;
        binding.lastApproved = null;
        return true;
    }

    /** Decision that authorized the next execute; cleared after personal delta report. */
    public synchronized Protocol.EventChoiceDecisionPayload consumeLastApproved(Object event) {
        Binding binding = bindings.get(event);
        if (binding == null) return null;
        Protocol.EventChoiceDecisionPayload d = binding.lastApproved;
        binding.lastApproved = null;
        return d;
    }

    public synchronized Protocol.EventInterfacePayload interfaceFor(Object event) {
        Binding binding = bindings.get(event);
        return binding != null ? binding.iface : null;
    }

    /**
     * Arms the gate from an out-of-band approved request (console diagnostics) so the next
     * buttonEffect / forced replay can execute once and emit a personal result.
     */
    public synchronized boolean armApprovedRequest(Object event,
                                                   Protocol.EventChoiceRequestPayload request,
                                                   Protocol.EventChoiceDecisionPayload decision) {
        Binding binding = bindings.get(event);
        if (binding == null || request == null || decision == null) return false;
        if (!matches(request, decision)) return false;
        binding.pending = request;
        binding.permitNext = true;
        binding.lastApproved = copyDecision(decision);
        binding.lastOptionIndex = decision.optionIndex;
        return true;
    }

    public synchronized int lastOptionIndex(Object event) {
        Binding binding = bindings.get(event);
        return binding != null ? binding.lastOptionIndex : 0;
    }

    public synchronized boolean isBound(Object event) {
        return event != null && bindings.containsKey(event);
    }

    public synchronized Object findEventForDecision(Protocol.EventChoiceDecisionPayload decision) {
        Binding binding = bindingFor(decision);
        if (binding == null) return null;
        for (Map.Entry<Object, Binding> entry : bindings.entrySet()) {
            if (entry.getValue() == binding) return entry.getKey();
        }
        return null;
    }

    private Binding bindingFor(Protocol.EventChoiceDecisionPayload decision) {
        if (decision == null) return null;
        for (Binding binding : bindings.values()) {
            if (binding.iface.eventInstanceId.equals(decision.eventInstanceId)) return binding;
        }
        return null;
    }

    private static Protocol.EventChoiceDecisionPayload copyDecision(
        Protocol.EventChoiceDecisionPayload source) {
        Protocol.EventChoiceDecisionPayload copy = new Protocol.EventChoiceDecisionPayload();
        copy.eventInstanceId = source.eventInstanceId;
        copy.partyId = source.partyId;
        copy.requestId = source.requestId;
        copy.uiStep = source.uiStep;
        copy.optionIndex = source.optionIndex;
        copy.reason = source.reason;
        return copy;
    }

    private static boolean validInterface(Protocol.EventInterfacePayload iface) {
        return iface != null && !empty(iface.eventInstanceId) && !empty(iface.partyId)
            && iface.options != null && iface.options.length > 0;
    }

    private static boolean enabled(Protocol.EventInterfacePayload iface, int optionIndex) {
        for (Protocol.EventOptionInfo option : iface.options) {
            if (option != null && option.index == optionIndex) {
                return option.enabled && !option.disabled;
            }
        }
        return false;
    }

    private static boolean isKnownStep(String uiStep) {
        return STEP_BUTTON.equals(uiStep) || STEP_CARD_SELECT.equals(uiStep)
            || STEP_TARGET_SELECT.equals(uiStep) || STEP_CONFIRM.equals(uiStep);
    }

    private static boolean matches(Protocol.EventChoiceRequestPayload request,
                                   Protocol.EventChoiceDecisionPayload decision) {
        return request != null && request.eventInstanceId.equals(decision.eventInstanceId)
            && request.partyId.equals(decision.partyId)
            && request.requestId.equals(decision.requestId)
            && request.uiStep.equals(decision.uiStep)
            && request.optionIndex == decision.optionIndex;
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
