package dream;

import java.awt.Color;
import java.awt.Font;

/**
 * One line of text on screen.
 *
 * <p>Only ever mutated on the Swing event thread, which is also the thread that
 * paints it, so no extra locking is needed.
 */
public class TextData {

    public String text;
    public int x;
    public int y;
    public int size;
    public Color color;
    public Font font;

    public TextData(String text, int x, int y, int size, Color color, Font font) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.size = size;
        this.color = color;
        this.font = font;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setY(int y) {
        this.y = y;
    }
}
