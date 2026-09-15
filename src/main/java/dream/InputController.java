package dream;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/**
 * Turns raw keyboard and wheel events into terminal actions.
 *
 * <p>Split out of the main class so the story code no longer has to implement
 * three listener interfaces it mostly left empty.
 */
public class InputController extends KeyAdapter implements MouseWheelListener {

    private static final int SCROLL_SPEED = 25;

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_BACK_SPACE:
                Terminal.handleBackspace();
                break;
            case KeyEvent.VK_ENTER:
                Terminal.handleEnter();
                break;
            default:
                break;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        char c = e.getKeyChar();
        // Backspace and Enter arrive here too; they are handled in keyPressed.
        if (c >= 32 && c <= 126) {
            Terminal.handleCharTyped(c);
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        int amount = -e.getWheelRotation() * SCROLL_SPEED;
        // Only click when the view actually moved, so hitting the end of the
        // log is silent rather than rattling.
        if (Terminal.scroll(amount)) {
            AudioManager.playRandomSfx(AudioManager.SCROLL_SOUNDS);
        }
    }
}
