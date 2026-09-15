import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.awt.Font;
import java.net.URI;
import java.net.URL;
import java.io.InputStream;
import java.awt.GraphicsEnvironment;

public class textMaker {
    
    
    // Keep track of lines so we can push them up later
    // Keep track of lines so we can push them up later
    // Change this line in textMaker.java
public static List<MyProgram.TextData> activeLines = new ArrayList<>();
    private static final int LINE_HEIGHT = 40; // Gap between separate messages
    private static final int WRAP_HEIGHT = 25; // Tighter gap for wrapped overflow lines
    private static final int START_X = 50;
    private static Queue<MessageRequest> messageQueue = new LinkedList<>();
    private static boolean isTyping = false;

    // Helper class to hold the information for each text request
    private static class MessageRequest {
        String text;
        int speed;
        int fontSize;
        Color color;
        int pushUpAmount;
        Font font;

        public MessageRequest(String text, int speed, int fontSize, Color color, int pushUpAmount, Font font) {
            this.text = text;
            this.speed = speed;
            this.fontSize = fontSize;
            this.color = color;
            this.pushUpAmount = pushUpAmount;
            this.font = font;
        }
    }
    /**
     * Public method to request new text. It adds the text to a waiting list.
     */
    /**
     * Public method to request new text. It splits long strings into wrapped lines 
     * before adding them to the waiting list.
     */
     
     
      public static void placeText(String fullText, int speed, int fontSize, Color color) {
        if (MyProgram.canvas == null) return;

       Font font = null;
try {
    URL fontUrl = URI.create("https://codehs.com/uploads/03d1c39ed57756d1adeb98be31f469c8").toURL();
    InputStream is = fontUrl.openStream();
    
    // 1. Create the base font from the stream
    Font baseFont = Font.createFont(Font.TRUETYPE_FONT, is);
    
    // 2. NEW: Register the font globally so the canvas can actually use it!
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    ge.registerFont(baseFont);
    
    // 3. Scale it to your requested size
    font = baseFont.deriveFont(Font.PLAIN, (float) fontSize);
    
} catch (Exception e) {
    System.out.println("Could not load custom font. Using default.");
    font = new Font("Monospaced", Font.PLAIN, fontSize);
}

// Now you can safely get the metrics!
java.awt.FontMetrics metrics = MyProgram.canvas.getFontMetrics(font);
        int maxWidth = MyProgram.canvas.getWidth() - START_X - 50;

        String[] words = fullText.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        // NEW: Track if this is the first line of the message
        boolean isFirstLine = true; 

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine.toString() + " " + word;
            
            if (metrics.stringWidth(testLine) > maxWidth && currentLine.length() > 0) {
                // Determine the gap based on if it's the first line or a wrapped line
                int gap = isFirstLine ? LINE_HEIGHT : WRAP_HEIGHT;
                
                messageQueue.add(new MessageRequest(currentLine.toString(), speed, fontSize, color, gap, font));
                currentLine = new StringBuilder(word); 
                
                isFirstLine = false; // Any lines after this are definitely wrapped overflow
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }

        if (currentLine.length() > 0) {
        int gap = isFirstLine ? LINE_HEIGHT : WRAP_HEIGHT;
        messageQueue.add(new MessageRequest(currentLine.toString(), speed, fontSize, color, gap, font));
    }

        processNextMessage();
    }
      /**
     * Internal method that handles the actual typing and queue management.
     */
    private static void processNextMessage() {
        // If we are currently typing, or if the queue is empty, do nothing and wait.
        if (isTyping || messageQueue.isEmpty()) {
            return;
        }

        // Lock the system so no other messages interrupt
        isTyping = true;
        
        // Remove the next message from the front of the line
        MessageRequest req = messageQueue.poll();

        if (MyProgram.canvas == null) {
            isTyping = false;
            return;
        }
        

        // 1. Move all previous lines UP by the specific amount requested by this line
        for (MyProgram.TextData line : activeLines) {
            line.setY(line.y - req.pushUpAmount); // CHANGED THIS LINE
        }

// 2. Determine starting Y (Bottom of screen)
Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
int screenHeight = screenSize.height;

// NEW: Add the global scroll offset so the spawn point moves with the text!
int startY = (screenHeight - 100) + MyProgram.globalScrollOffset;

       // Inside textMaker's processNextMessage() method:
MyProgram.TextData newLine = new MyProgram.TextData("", START_X, startY, req.fontSize, req.color, req.font);
        //newLine.setFont(req.font);
        activeLines.add(newLine);
        
        // 4. Add it to the canvas
        MyProgram.canvas.addTextToLayer("BACKGROUND_SHAPES", newLine);

        // 5. Typing Animation Timer
        Timer timer = new Timer(req.speed, null);
        final int[] charIndex = {0}; 

        timer.addActionListener(e -> {
            if (charIndex[0] < req.text.length()) {
                // Type the next letter
                newLine.setText(newLine.text + req.text.charAt(charIndex[0]));
                charIndex[0]++;
                MyProgram.canvas.repaint();
                Toolkit.getDefaultToolkit().sync();
            } else {
                // We finished typing this line
                timer.stop(); 
                isTyping = false; // Unlock the system
                
                // Immediately check if there is another message waiting in the queue
                processNextMessage(); 
            }
        });
        
        timer.start();
    }
}