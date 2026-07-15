package crossspire.ui;

import basemod.BaseMod;

public class ServerPicker {

    public static String roomCode = "CROSS";
    public static String hostIp = "127.0.0.1";
    public static int hostPort = 54321;
    public static boolean autoConnect = false;
    public static boolean isRoomHost = true;
    public static boolean isStageHost = true;

    static {
        String stageProp = System.getProperty("crossspire.stage.host");
        if (stageProp != null && "false".equals(stageProp.trim())) {
            isStageHost = false;
        }
        String roomProp = System.getProperty("crossspire.room.host");
        if (roomProp != null && "false".equals(roomProp.trim())) {
            isRoomHost = false;
        }
        try {
            BaseMod.logger.info("ServerPicker isRoomHost=" + isRoomHost + " isStageHost=" + isStageHost);
        } catch (Throwable ignored) {}
    }
}
