package crossspire.ui;

import basemod.BaseMod;

public class ServerPicker {

    public static String serverUrl = "ws://127.0.0.1:9876";
    public static String roomCode = "CROSS";
    public static boolean autoConnect = false;
    public static boolean isStageHost = true;

    static {
        String propVal = System.getProperty("crossspire.stage.host");
        if (propVal != null && "false".equals(propVal.trim())) {
            isStageHost = false;
        }
        BaseMod.logger.info("ServerPicker isStageHost=" + isStageHost + " (prop=" + propVal + ")");
    }
}
