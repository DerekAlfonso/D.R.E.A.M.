package dream;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;

/**
 * D.R.E.A.M - Digital Recreation of Emotional Aptitude Model.
 *
 * <p>Entry point. Builds the window on the Swing event thread, then hands the
 * story to a worker thread so its pauses never freeze rendering or input. The
 * original did all of this on one thread, which is why the window stopped
 * responding while audio played.
 *
 * <p>Run with {@code --debug} for verbose logging.
 */
public final class Main {

    private static JFrame frame;
    private static Game game;

    private Main() { }

    public static void main(String[] args) {
        for (String arg : args) {
            if ("--debug".equalsIgnoreCase(arg)) {
                Log.debugMode = true;
            }
        }

        // Crisper text on high-DPI Windows displays.
        System.setProperty("sun.java2d.uiScale.enabled", "false");

        Settings.load();
        SaveState.load();

        // Decode the short effects up front so the first keystroke is instant.
        AudioManager.preloadAsync(AudioManager.KEYBOARD_CLICKS);
        AudioManager.preloadAsync(AudioManager.SCROLL_SOUNDS);

        try {
            SwingUtilities.invokeAndWait(Main::buildWindow);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (InvocationTargetException e) {
            Log.error("Could not open the window.", e.getCause());
            return;
        }

        // The boot whirr streams on its own thread, so the story starts on time.
        AudioManager.playMusic(AudioManager.STARTUP_WHIRR, false);

        game = new Game();
        Thread storyThread = new Thread(game, "dream-story");
        storyThread.setDaemon(true);
        storyThread.start();
    }

    private static void buildWindow() {
        Font terminalFont = Assets.loadTerminalFont();

        CurveCanvas canvas = new CurveCanvas();
        InputController input = new InputController();
        canvas.addKeyListener(input);
        canvas.addMouseWheelListener(input);

        Terminal.attach(canvas, terminalFont);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        canvas.setPreferredSize(new Dimension(
            (int) (screen.width * 0.8), (int) (screen.height * 0.8)));

        frame = new JFrame("D.R.E.A.M");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.add(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        frame.setVisible(true);

        // Scanlines need the real canvas size, which only exists once shown.
        canvas.rebuildScanlines();
        canvas.requestFocusInWindow();
    }

    /** Saves, releases the sound card and closes down cleanly. */
    public static void shutdown() {
        Log.info("Shutting down.");
        if (game != null) {
            game.stop();
        }
        Settings.save();
        SaveState.save();
        AudioManager.dispose();

        if (frame != null) {
            frame.dispose();
        }
        System.exit(0);
    }
}
