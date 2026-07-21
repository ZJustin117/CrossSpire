package crossspire.event;

import crossspire.network.Protocol;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** One-shot approval state for locally instantiated native event dialogs. */
public final class NativeEventApprovalGate {

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
        boolean permitNext;

        Binding(Protocol.EventInterfacePayload iface) {
            this.iface = iface;
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
        Binding binding = bindings.get(event);
        if (binding == null) return Attempt.execute();
        if (binding.permitNext) {
            binding.permitNext = false;
            binding.pending = null;
            return Attempt.execute();
        }
        if (binding.pending != null || !enabled(binding.iface, optionIndex)) return Attempt.block(null);

        Protocol.EventChoiceRequestPayload request = new Protocol.EventChoiceRequestPayload();
        request.eventInstanceId = binding.iface.eventInstanceId;
        request.partyId = binding.iface.partyId;
        request.requestId = binding.iface.eventInstanceId + ":"
            + requestSequence.incrementAndGet();
        request.uiStep = "buttonEffect";
        request.optionIndex = optionIndex;
        request.resourceHash = binding.iface.resourceHash;
        binding.pending = request;
        return Attempt.block(request);
    }

    public synchronized boolean approve(Protocol.EventChoiceDecisionPayload decision) {
        Binding binding = bindingFor(decision);
        if (binding == null || !matches(binding.pending, decision)) return false;
        binding.permitNext = true;
        return true;
    }

    public synchronized boolean reject(Protocol.EventChoiceDecisionPayload decision) {
        Binding binding = bindingFor(decision);
        if (binding == null || !matches(binding.pending, decision)) return false;
        binding.pending = null;
        binding.permitNext = false;
        return true;
    }

    private Binding bindingFor(Protocol.EventChoiceDecisionPayload decision) {
        if (decision == null) return null;
        for (Binding binding : bindings.values()) {
            if (binding.iface.eventInstanceId.equals(decision.eventInstanceId)) return binding;
        }
        return null;
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
