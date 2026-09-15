package dream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * What the AI remembers about the player between runs. Written to saveState.txt,
 * which the original created but never actually used.
 */
public final class SaveState {

    private static final Path FILE = Paths.get("saveState.txt");

    public static String username = null;
    public static String cpuName = null;
    public static String favoriteColor = null;

    private SaveState() { }

    public static void load() {
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            props.load(in);
        } catch (IOException e) {
            Log.warn("Could not read saveState.txt: " + e.getMessage());
            return;
        }

        username = emptyToNull(props.getProperty("username"));
        cpuName = emptyToNull(props.getProperty("cpuName"));
        favoriteColor = emptyToNull(props.getProperty("favoriteColor"));
        if (username != null) {
            Log.info("Loaded save state for " + username);
        }
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("username", nullToEmpty(username));
        props.setProperty("cpuName", nullToEmpty(cpuName));
        props.setProperty("favoriteColor", nullToEmpty(favoriteColor));

        try (OutputStream out = Files.newOutputStream(FILE)) {
            props.store(out, "D.R.E.A.M save state");
        } catch (IOException e) {
            Log.warn("Could not write saveState.txt: " + e.getMessage());
        }
    }

    public static boolean hasProfile() {
        return username != null && cpuName != null;
    }

    public static void reset() {
        username = null;
        cpuName = null;
        favoriteColor = null;
        save();
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
