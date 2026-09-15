package dream;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Finds game files whether we are running from the packaged jar or straight out
 * of the source folder.
 *
 * <p>Every asset the game needs is embedded in the jar, so nothing is fetched
 * over the network at runtime. The disk lookups below only exist so the game
 * still runs when launched from the source tree during development.
 */
public final class Assets {

    private Assets() { }

    /**
     * Opens an asset by its path under /assets, e.g. "audio/StartupWhirr.mp3".
     * Looks inside the jar first, then on disk.
     *
     * @return an open stream, or null if the asset genuinely isn't anywhere.
     */
    public static InputStream open(String relativePath) {
        InputStream fromJar = Assets.class.getResourceAsStream("/assets/" + relativePath);
        if (fromJar != null) {
            return new BufferedInputStream(fromJar);
        }

        String bare = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        String[] candidates = {
            "src/main/resources/assets/" + relativePath,
            "assets/" + relativePath,
            relativePath,
            bare
        };

        for (String candidate : candidates) {
            File f = new File(candidate);
            if (f.isFile()) {
                try {
                    return new BufferedInputStream(new FileInputStream(f));
                } catch (IOException ignored) {
                    // Try the next location.
                }
            }
        }
        return null;
    }

    /** Reads splash.txt into a list, skipping blanks and # comments. */
    public static List<String> loadSplashes() {
        List<String> splashes = new ArrayList<>();
        try (InputStream in = open("splash.txt")) {
            if (in != null) {
                Scanner reader = new Scanner(in, StandardCharsets.UTF_8.name());
                while (reader.hasNextLine()) {
                    String line = reader.nextLine().trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        splashes.add(line);
                    }
                }
            }
        } catch (IOException e) {
            Log.warn("Could not read splash.txt: " + e.getMessage());
        }
        if (splashes.isEmpty()) {
            splashes.add("Err 404: splash text not found");
        }
        return splashes;
    }

    /**
     * Loads the terminal typeface once, at startup.
     *
     * <p>The original shipped a "pixelFont.ttf" that was really a text file
     * holding a CodeHS URL, and re-downloaded the real font for every single
     * line of dialogue. The genuine TrueType file is now embedded, so this is
     * one local read with a monospaced fallback if anything goes wrong.
     */
    public static Font loadTerminalFont() {
        try (InputStream in = open("pixelFont.ttf")) {
            if (in != null) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, in);
                // Registering makes the face resolvable by name for metrics lookups.
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                Log.info("Loaded terminal font: " + font.getFontName());
                return font;
            }
            Log.warn("pixelFont.ttf not found; using the built-in monospaced font.");
        } catch (Exception e) {
            Log.warn("Could not load pixelFont.ttf (" + e.getMessage()
                + "); using the built-in monospaced font.");
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, 15);
    }
}
