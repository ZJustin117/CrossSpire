package crossspire.event;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import crossspire.network.StandardPacket;
import crossspire.reference.ContentValidator;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/** Gates the shared native event button dispatch until RoomHost approval arrives. */
public final class NativeEventApprovalPatches {

    private static final NativeEventApprovalGate GATE = new NativeEventApprovalGate();

    private NativeEventApprovalPatches() {}

    public static boolean bind(AbstractEvent event, Protocol.EventInterfacePayload iface) {
        return GATE.bind(event, iface);
    }

    public static boolean bindIfMatching(AbstractEvent event, Protocol.EventInterfacePayload iface) {
        if (event == null || iface == null || iface.eventClass == null
            || !iface.eventClass.equals(event.getClass().getName())) {
            return false;
        }
        String localHash = ContentValidator.hashClass(iface.eventClass);
        if (localHash.isEmpty() || !localHash.equals(iface.resourceHash)) return false;
        return bind(event, iface);
    }

    public static boolean intercept(AbstractEvent event, int optionIndex) {
        NativeEventApprovalGate.Attempt attempt = GATE.beforeButtonEffect(event, optionIndex);
        if (attempt.execute) return true;
        RoomEventDialog.waitForInput = true;
        if (attempt.request != null) send(attempt.request);
        return false;
    }

    public static void approved(Protocol.EventChoiceDecisionPayload decision) {
        if (GATE.approve(decision)) {
            RoomEventDialog.waitForInput = false;
        }
    }

    public static void rejected(Protocol.EventChoiceDecisionPayload decision) {
        if (GATE.reject(decision)) {
            RoomEventDialog.waitForInput = true;
        }
    }

    private static void send(Protocol.EventChoiceRequestPayload request) {
        StandardPacket packet = EventChoiceSender.requestPacket(request);
        String raw = StandardPacket.toJson(packet);
        if (CrossSpireMod.isRoomHost() && CrossSpireMod.messageRouter != null) {
            CrossSpireMod.messageRouter.route(raw);
        } else {
            CrossSpireMod.send(raw);
        }
        BaseMod.logger.info("NativeEventApproval request event=" + request.eventInstanceId
            + " request=" + request.requestId);
    }

    @SpirePatch(clz = AbstractEvent.class, method = "update", paramtypez = {})
    public static class ButtonEffectDispatch {
        @SpireInstrumentPatch
        public static ExprEditor instrument() {
            return new ExprEditor() {
                @Override
                public void edit(MethodCall call) throws CannotCompileException {
                    if ("buttonEffect".equals(call.getMethodName())
                        && "(I)V".equals(call.getSignature())) {
                        call.replace("{ if (crossspire.event.NativeEventApprovalPatches.intercept($0, $1)) { $proceed($$); } }");
                    }
                }
            };
        }
    }
}
