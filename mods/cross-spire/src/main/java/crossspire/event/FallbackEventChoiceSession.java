package crossspire.event;

import crossspire.network.Protocol;
import java.util.concurrent.atomic.AtomicLong;

/** Pure session for fallback UI choices that still use the T7.4 approval protocol. */
public final class FallbackEventChoiceSession {

    private Protocol.EventInterfacePayload iface;
    private Protocol.EventChoiceRequestPayload pending;
    private final AtomicLong requestSequence = new AtomicLong();

    public synchronized void open(Protocol.EventInterfacePayload iface) {
        this.iface = valid(iface) ? iface : null;
        this.pending = null;
    }

    public synchronized void clear() {
        iface = null;
        pending = null;
    }

    public synchronized boolean isOpen() {
        return iface != null;
    }

    public synchronized Protocol.EventInterfacePayload interfacePayload() {
        return iface;
    }

    public synchronized Protocol.EventChoiceRequestPayload pendingRequest() {
        return pending;
    }

    public synchronized Protocol.EventChoiceRequestPayload choose(int optionIndex) {
        if (iface == null || pending != null || !enabled(optionIndex)) return null;
        Protocol.EventChoiceRequestPayload request = new Protocol.EventChoiceRequestPayload();
        request.eventInstanceId = iface.eventInstanceId;
        request.partyId = iface.partyId;
        request.requestId = iface.eventInstanceId + ":fallback:"
            + requestSequence.incrementAndGet();
        request.uiStep = "buttonEffect";
        request.optionIndex = optionIndex;
        request.resourceHash = iface.resourceHash;
        pending = request;
        return request;
    }

    public synchronized boolean approve(Protocol.EventChoiceDecisionPayload decision) {
        if (!matches(pending, decision)) return false;
        pending = null;
        return true;
    }

    public synchronized boolean reject(Protocol.EventChoiceDecisionPayload decision) {
        if (!matches(pending, decision)) return false;
        pending = null;
        return true;
    }

    private boolean enabled(int optionIndex) {
        if (iface.options == null) return false;
        for (Protocol.EventOptionInfo option : iface.options) {
            if (option != null && option.index == optionIndex) {
                return option.enabled && !option.disabled;
            }
        }
        return false;
    }

    private static boolean matches(Protocol.EventChoiceRequestPayload request,
                                   Protocol.EventChoiceDecisionPayload decision) {
        return request != null && decision != null
            && request.eventInstanceId.equals(decision.eventInstanceId)
            && request.partyId.equals(decision.partyId)
            && request.requestId.equals(decision.requestId)
            && request.uiStep.equals(decision.uiStep)
            && request.optionIndex == decision.optionIndex;
    }

    private static boolean valid(Protocol.EventInterfacePayload iface) {
        return iface != null && iface.eventInstanceId != null && !iface.eventInstanceId.isEmpty()
            && iface.partyId != null && !iface.partyId.isEmpty()
            && iface.options != null && iface.options.length > 0;
    }
}
