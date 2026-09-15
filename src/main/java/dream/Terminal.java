package dream;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

/**
 * The scrolling terminal: queues messages, types them out a character at a
 * time, wraps long lines and collects player input.
 *
 * <p>Threading rules, which the CodeHS version did not have and which caused
 * flicker and lost characters:
 * <ul>
 *   <li>Every field here is touched only on the Swing event thread.</li>
 *   <li>The story thread calls {@link #print} (returns immediately),
 *       {@link #awaitIdle} and {@link #prompt} (both block).</li>
 * </ul>
 */
public final class Terminal {

    // Layout is authored against this canvas height and scaled up from there,
    // so the terminal stays readable on a 4K panel instead of shrinking to
    // nothing. Everything below is a base value in "reference pixels".
    private static final int REFERENCE_HEIGHT = 900;
    private static final double MIN_AUTO_SCALE = 1.0;
    private static final double MAX_AUTO_SCALE = 3.5;

    /** Gap between separate messages. */
    private static final int BASE_LINE_HEIGHT = 40;

    /** Tighter gap for the overflow lines of a wrapped message. */
    private static final int BASE_WRAP_HEIGHT = 25;

    /** Distance from the bottom of the canvas where new lines appear. */
    private static final int BASE_HOME_MARGIN = 100;

    /** Closest the oldest line may be scrolled to the top of the screen. */
    private static final int BASE_TOP_MARGIN = 60;

    /**
     * Widest the readable text column is allowed to get, in reference pixels.
     * Roughly 85 characters, which keeps the column near the middle of a wide
     * screen instead of starting hard against the left edge.
     */
    private static final int BASE_MAX_COLUMN = 700;

    /** Shortest gap between two typing clicks, so fast text is not a buzz. */
    private static final int CLICK_INTERVAL_MS = 80;

    /** Lines kept in memory. Older ones are unreachable anyway once clamped. */
    private static final int MAX_RETAINED_LINES = 400;

    private static CurveCanvas canvas;
    private static Font baseFont = new Font(Font.MONOSPACED, Font.PLAIN, 15);

    private static final List<TextData> activeLines = new ArrayList<>();
    private static final Queue<MessageRequest> messageQueue = new LinkedList<>();
    private static boolean isTyping = false;
    private static int scrollOffset = 0;
    private static long lastClickAt = 0L;

    /** Story threads parked in awaitIdle(), released when the queue drains. */
    private static final List<CountDownLatch> idleWaiters = new ArrayList<>();

    private static boolean awaitingInput = false;
    private static String currentInput = "";
    private static String promptSymbol = "> ";
    private static TextData inputLine = null;
    private static BlockingQueue<String> inputResult = null;

    private static double speedMultiplier = 1.0;
    private static boolean typingSounds = true;

    private Terminal() { }

    private static final class MessageRequest {
        final String text;
        final int speed;
        final int fontSize;
        final Color color;
        final int pushUpAmount;
        final Font font;

        MessageRequest(String text, int speed, int fontSize, Color color,
                       int pushUpAmount, Font font) {
            this.text = text;
            this.speed = speed;
            this.fontSize = fontSize;
            this.color = color;
            this.pushUpAmount = pushUpAmount;
            this.font = font;
        }
    }

    // ----------------------------------------------------------------- setup

    public static void attach(CurveCanvas target, Font font) {
        canvas = target;
        baseFont = font;
    }

    public static void setSpeedMultiplier(double multiplier) {
        speedMultiplier = Math.max(0.1, Math.min(5.0, multiplier));
    }

    public static double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public static void setTypingSounds(boolean enabled) {
        typingSounds = enabled;
    }

    public static boolean isTypingSounds() {
        return typingSounds;
    }

    // ---------------------------------------------------------------- layout

    /**
     * How much to enlarge everything for the current canvas, combining an
     * automatic factor derived from the screen height with the player's own
     * textScale preference.
     */
    private static double uiScale() {
        double auto = MIN_AUTO_SCALE;
        if (canvas != null && canvas.getHeight() > 0) {
            auto = canvas.getHeight() / (double) REFERENCE_HEIGHT;
            auto = Math.max(MIN_AUTO_SCALE, Math.min(MAX_AUTO_SCALE, auto));
        }
        return auto * Settings.textScale;
    }

    private static int scaled(int baseValue) {
        return (int) Math.round(baseValue * uiScale());
    }

    private static int scaledFontSize(int baseSize) {
        return Math.max(8, (int) Math.round(baseSize * uiScale()));
    }

    /**
     * Width of the readable text column. Capped so lines do not stretch across
     * an ultrawide display, which is unreadable and looks nothing like a
     * terminal.
     */
    private static int contentWidth() {
        int canvasWidth = canvas != null && canvas.getWidth() > 0
            ? canvas.getWidth() : 1280;
        int cap = scaled(BASE_MAX_COLUMN);
        int available = canvasWidth - scaled(80);
        return Math.max(320, Math.min(available, cap));
    }

    /** Left edge of the text column, centred within the canvas. */
    private static int startX() {
        int canvasWidth = canvas != null && canvas.getWidth() > 0
            ? canvas.getWidth() : 1280;
        return Math.max(scaled(20), (canvasWidth - contentWidth()) / 2);
    }

    /**
     * Recentres and rescales existing lines after the window changes size.
     * Called by the canvas on resize.
     */
    public static void relayout() {
        if (canvas == null) {
            return;
        }
        int x = startX();
        for (TextData line : activeLines) {
            line.x = x;
            line.font = baseFont.deriveFont(Font.PLAIN,
                (float) scaledFontSize(line.size));
        }
        canvas.repaint();
    }

    // --------------------------------------------------------------- output

    /**
     * Queues a message. Returns immediately; the text types itself out on the
     * event thread. Long messages are wrapped to the canvas width first.
     *
     * @param speed milliseconds between characters
     */
    public static void print(String fullText, int speed, int fontSize, Color color) {
        if (canvas == null) {
            return;
        }
        final String safeText = fullText == null ? "" : fullText;
        SwingUtilities.invokeLater(() -> enqueue(safeText, speed, fontSize, color));
    }

    /** Convenience for a blank spacer line. */
    public static void blankLine() {
        print(" ", 1, 15, Color.WHITE);
    }

    /**
     * Adds a line that rewrites itself in place instead of typing out, for
     * things like a progress bar that has to update on one row.
     *
     * <p>Blocks until queued dialogue has finished so the line does not appear
     * in the middle of a sentence.
     */
    public static StatusLine openStatusLine(int fontSize, Color color) {
        awaitIdle();

        StatusLine handle = new StatusLine();
        SwingUtilities.invokeLater(() -> {
            for (TextData line : activeLines) {
                line.setY(line.y - scaled(BASE_LINE_HEIGHT));
            }

            Font font = baseFont.deriveFont(Font.PLAIN, (float) scaledFontSize(fontSize));
            TextData data = new TextData("", startX(), homeY(), fontSize, color, font);
            activeLines.add(data);
            canvas.addTextToLayer(CurveCanvas.TEXT_LAYER, data);
            trimHistory();
            handle.bind(data);
        });
        return handle;
    }

    /**
     * A handle to one rewritable line. Safe to call from the story thread: the
     * update is marshalled onto the event thread, and because that queue is
     * ordered it always runs after the line itself has been created.
     */
    public static final class StatusLine {
        private TextData data;

        private void bind(TextData data) {
            this.data = data;
        }

        /** Replaces the whole line. Keep the text a fixed width so it does not jitter. */
        public void set(String text) {
            SwingUtilities.invokeLater(() -> {
                if (data != null) {
                    data.setText(text);
                    canvas.repaint();
                }
            });
        }
    }

    private static void enqueue(String fullText, int speed, int fontSize, Color color) {
        Font font = baseFont.deriveFont(Font.PLAIN, (float) scaledFontSize(fontSize));
        FontMetrics metrics = canvas.getFontMetrics(font);
        int maxWidth = contentWidth();

        int scaledSpeed = Math.max(1, (int) Math.round(speed / speedMultiplier));
        int lineGap = scaled(BASE_LINE_HEIGHT);
        int wrapGap = scaled(BASE_WRAP_HEIGHT);

        String[] words = fullText.split(" ");
        StringBuilder currentLine = new StringBuilder();
        boolean isFirstLine = true;

        for (String word : words) {
            String testLine = currentLine.length() == 0
                ? word
                : currentLine.toString() + " " + word;

            if (metrics.stringWidth(testLine) > maxWidth && currentLine.length() > 0) {
                int gap = isFirstLine ? lineGap : wrapGap;
                messageQueue.add(new MessageRequest(currentLine.toString(),
                    scaledSpeed, fontSize, color, gap, font));
                currentLine = new StringBuilder(word);
                isFirstLine = false;
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }

        if (currentLine.length() > 0) {
            int gap = isFirstLine ? lineGap : wrapGap;
            messageQueue.add(new MessageRequest(currentLine.toString(),
                scaledSpeed, fontSize, color, gap, font));
        }

        processNextMessage();
    }

    /** Runs on the event thread. Types one queued line, then chains to the next. */
    private static void processNextMessage() {
        if (isTyping || messageQueue.isEmpty() || canvas == null) {
            return;
        }

        isTyping = true;
        MessageRequest req = messageQueue.poll();

        for (TextData line : activeLines) {
            line.setY(line.y - req.pushUpAmount);
        }

        TextData newLine = new TextData("", startX(), homeY(), req.fontSize,
            req.color, req.font);
        activeLines.add(newLine);
        canvas.addTextToLayer(CurveCanvas.TEXT_LAYER, newLine);
        trimHistory();

        final int[] charIndex = {0};
        Timer timer = new Timer(req.speed, null);
        timer.addActionListener(e -> {
            if (charIndex[0] < req.text.length()) {
                char c = req.text.charAt(charIndex[0]);
                newLine.setText(newLine.text + c);
                charIndex[0]++;
                if (c != ' ') {
                    playTypingClick();
                }
                canvas.repaint();
            } else {
                timer.stop();
                isTyping = false;
                if (messageQueue.isEmpty()) {
                    releaseIdleWaiters();
                }
                processNextMessage();
            }
        });
        timer.setInitialDelay(req.speed);
        timer.start();
    }

    /** Rate-limited so a fast crawl does not turn into a solid buzz. */
    private static void playTypingClick() {
        if (!typingSounds) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClickAt >= CLICK_INTERVAL_MS) {
            lastClickAt = now;
            AudioManager.playRandomSfx(AudioManager.KEYBOARD_CLICKS);
        }
    }

    private static int homeY() {
        int height = canvas.getHeight() > 0 ? canvas.getHeight() : 800;
        return (height - scaled(BASE_HOME_MARGIN)) + scrollOffset;
    }

    private static void trimHistory() {
        if (activeLines.size() > MAX_RETAINED_LINES) {
            activeLines.subList(0, activeLines.size() - MAX_RETAINED_LINES).clear();
            canvas.pruneText(MAX_RETAINED_LINES);
        }
    }

    // ----------------------------------------------------------- idle waiting

    /**
     * Blocks the calling story thread until everything queued has finished
     * typing. Prevents a prompt from appearing in the middle of a sentence.
     */
    public static void awaitIdle() {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("awaitIdle() would deadlock the event thread");
        }

        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            if (!isTyping && messageQueue.isEmpty()) {
                latch.countDown();
            } else {
                idleWaiters.add(latch);
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void releaseIdleWaiters() {
        for (CountDownLatch latch : idleWaiters) {
            latch.countDown();
        }
        idleWaiters.clear();
    }

    // ---------------------------------------------------------------- input

    /**
     * Shows the input caret and blocks the story thread until the player
     * presses Enter.
     *
     * @return exactly what they typed, never null
     */
    public static String prompt() {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("prompt() would deadlock the event thread");
        }

        awaitIdle();

        BlockingQueue<String> result = new ArrayBlockingQueue<>(1);
        SwingUtilities.invokeLater(() -> beginInput(result));

        try {
            return result.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static void beginInput(BlockingQueue<String> result) {
        awaitingInput = true;
        currentInput = "";
        inputResult = result;

        for (TextData line : activeLines) {
            line.setY(line.y - scaled(BASE_LINE_HEIGHT));
        }

        Font font = baseFont.deriveFont(Font.PLAIN, (float) scaledFontSize(15));
        inputLine = new TextData(promptSymbol, startX(), homeY(), 15, Color.GREEN, font);
        activeLines.add(inputLine);
        canvas.addTextToLayer(CurveCanvas.TEXT_LAYER, inputLine);
        canvas.repaint();
    }

    /** Called from the key listener on the event thread. */
    public static void handleCharTyped(char c) {
        if (!awaitingInput || inputLine == null) {
            return;
        }
        if (c >= 32 && c <= 126) {
            currentInput += c;
            inputLine.setText(promptSymbol + currentInput);
            AudioManager.playRandomSfx(AudioManager.KEYBOARD_CLICKS);
            canvas.repaint();
        }
    }

    /** Called from the key listener on the event thread. */
    public static void handleBackspace() {
        if (!awaitingInput || inputLine == null || currentInput.isEmpty()) {
            return;
        }
        currentInput = currentInput.substring(0, currentInput.length() - 1);
        inputLine.setText(promptSymbol + currentInput);
        AudioManager.playRandomSfx(AudioManager.KEYBOARD_CLICKS);
        canvas.repaint();
    }

    /** Called from the key listener on the event thread. */
    public static void handleEnter() {
        if (!awaitingInput) {
            return;
        }
        awaitingInput = false;

        String submitted = currentInput;
        inputLine = null;
        currentInput = "";

        AudioManager.playRandomSfx(AudioManager.KEYBOARD_CLICKS);

        if (inputResult != null) {
            inputResult.offer(submitted);
            inputResult = null;
        }
    }

    public static boolean isAwaitingInput() {
        return awaitingInput;
    }

    // --------------------------------------------------------------- scroll

    /**
     * Scrolls the log, clamped so the newest line can never be pushed above its
     * resting position and the oldest can never be dragged below the top edge.
     * Without this the player could scroll the conversation into empty space and
     * lose it, which was a known rough edge in the original.
     *
     * @param delta positive scrolls back towards older text
     * @return true if anything actually moved, so the caller knows to click
     */
    public static boolean scroll(int delta) {
        if (canvas == null || activeLines.isEmpty()) {
            return false;
        }

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (TextData line : activeLines) {
            minY = Math.min(minY, line.y);
            maxY = Math.max(maxY, line.y);
        }

        int height = canvas.getHeight() > 0 ? canvas.getHeight() : 800;
        int restingY = height - scaled(BASE_HOME_MARGIN);

        // Never scroll the newest line above where it normally sits.
        int minDelta = restingY - maxY;
        // Never drag the oldest line below the top margin.
        int maxDelta = scaled(BASE_TOP_MARGIN) - minY;

        if (maxDelta < minDelta) {
            // Log is shorter than one screen: pin it in place.
            maxDelta = minDelta;
        }

        int clamped = Math.max(minDelta, Math.min(maxDelta, delta));
        if (clamped == 0) {
            return false;
        }

        scrollOffset += clamped;
        canvas.scrollAllText(clamped);
        return true;
    }

    /** Wipes the screen, for returning to the boot menu. */
    public static void clear() {
        SwingUtilities.invokeLater(() -> {
            messageQueue.clear();
            activeLines.clear();
            canvas.pruneText(0);
            scrollOffset = 0;
            canvas.repaint();
        });
    }
}
