package dream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Player-adjustable options, stored next to the game as plain text so they can
 * be edited by hand. Backs the settings.bin entry on the boot menu, which was
 * previously a dead option that only printed to the console.
 */
public final class Settings {

    private static final Path FILE = Paths.get("settings.txt");

    public static float sfxVolume = 0.7f;
    public static float musicVolume = 0.5f;
    public static double textSpeed = 1.0;
    public static boolean typingSounds = true;

    /**
     * Extra text magnification on top of the automatic scaling the terminal
     * derives from the screen height. 1.0 leaves the automatic size alone.
     */
    public static double textScale = 1.0;

    private Settings() { }

    public static void load() {
        Properties props = new Properties();
        if (Files.isRegularFile(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                props.load(in);
            } catch (IOException e) {
                Log.warn("Could not read settings.txt: " + e.getMessage());
            }
        }

        sfxVolume = readFloat(props, "sfxVolume", sfxVolume);
        musicVolume = readFloat(props, "musicVolume", musicVolume);
        textSpeed = readDouble(props, "textSpeed", textSpeed);
        textScale = clampScale(readDouble(props, "textScale", textScale));
        typingSounds = Boolean.parseBoolean(
            props.getProperty("typingSounds", String.valueOf(typingSounds)));

        apply();
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("sfxVolume", String.valueOf(sfxVolume));
        props.setProperty("musicVolume", String.valueOf(musicVolume));
        props.setProperty("textSpeed", String.valueOf(textSpeed));
        props.setProperty("textScale", String.valueOf(textScale));
        props.setProperty("typingSounds", String.valueOf(typingSounds));

        try (OutputStream out = Files.newOutputStream(FILE)) {
            props.store(out, "D.R.E.A.M settings");
        } catch (IOException e) {
            Log.warn("Could not write settings.txt: " + e.getMessage());
        }
    }

    /** Pushes the current values into the systems that use them. */
    public static void apply() {
        AudioManager.setSfxVolume(sfxVolume);
        AudioManager.setMusicVolume(musicVolume);
        Terminal.setSpeedMultiplier(textSpeed);
        Terminal.setTypingSounds(typingSounds);
        // Text size may have changed, so recentre and rescale what is on screen.
        Terminal.relayout();
    }

    public static double clampScale(double value) {
        return Math.max(0.5, Math.min(3.0, value));
    }

    private static float readFloat(Properties props, String key, float fallback) {
        try {
            return Float.parseFloat(props.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double readDouble(Properties props, String key, double fallback) {
        try {
            return Double.parseDouble(props.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
