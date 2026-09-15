package dream;

/** Console logging that stays quiet unless debug mode is on. */
public final class Log {

    public static boolean debugMode = false;

    private Log() { }

    public static void info(String message) {
        if (debugMode) {
            System.out.println("[dream] " + message);
        }
    }

    public static void warn(String message) {
        System.out.println("[dream] " + message);
    }

    public static void error(String message, Throwable cause) {
        System.err.println("[dream] " + message);
        if (debugMode && cause != null) {
            cause.printStackTrace();
        }
    }
}
