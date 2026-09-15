import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.QuadCurve2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.Random;
import java.io.InputStream;
import java.awt.GraphicsEnvironment;
import java.applet.*;
//package net.codejava.sound;
import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;



/*
D.R.E.A.M, in case I forget, stands for:
Digital Recreation of Emotional Aptitude Model
(I know, the AI commentary is on the nose, sue me)

Notes (in no particular order):
 - Should I force a 4:3 or 16:10 aspect ratio, project currently 
    feels weird at certain sizes and aspect ratios
 - Text has a "flicker" effect when scrolling, though I might leave 
    it as is as I kind of like the effect, its kinda retro lol
 - I want a fake loading bar to feel like the game has to load 
    like in the Unreliable Revelations demo, after opening the 
    projectDream.iso (e.g starting the game)
 - more splashes, like a lot more, most should be pop culture
    references, but I guess a few story related ones wouldnt hurt.
 - I want to lock scrolling to not allow text to break to far off 
    screen and leaving the user lost
 
*/
public class MyProgram implements MouseListener, MouseWheelListener, KeyListener {
    public static boolean debugMode = true;
    public static CurveCanvas canvas;
public static Font dosFont;
public static int globalScrollOffset = 0;
public static boolean isWaitingForInput = false;
public static String currentInput = "";
public static String promptString = "> "; // The little symbol before the user type
public static TextData inputLineData = null; // Holds the live text object. reference currentInput for user query
public static String username = null;
public static String CPUname = null;
public static String favoriteColor = null;

/*
KeyboardClick1 = "https://codehs.com/uploads/12e9702b3f5e43ea688b7e3dd0d7efeb"
KeyboardClick2 = "https://codehs.com/uploads/85c9ed6e2a94f23169bdbbc01c4c96af"
KeyboardClick3 = "https://codehs.com/uploads/c27fc10bed49926334e0c9e62a97648c"

InitializeWhirr = "https://codehs.com/uploads/9d4b73b6de8a9192a116f30928571bd4"

ScrollSound1 = "https://codehs.com/uploads/54f947edf26af9006db5c31efe23fc56"
ScrollSound2 = "https://codehs.com/uploads/d3663a1d3b116a2f3952dd3c0e864148"
ScrollSound3 = "https://codehs.com/uploads/4fe237532c3ab54867153fe48986dc14"
ScrollSound4 = "https://codehs.com/uploads/82dbdc29cc80d1f6fe04ab30e68ac63a"
ScrollSound5 = "https://codehs.com/uploads/21b7b7638d3242125c8f0e307b8cee92"
ScrollSound6 = "https://codehs.com/uploads/e65f92c2fc174154ca91552f0f982046"
*/

    public static void main(String[] args) throws InterruptedException { 
                String audioFilePath = "https://codehs.com/uploads/1981fc4b1d2e4123e9cbe7ab8cc1962a";
        AudioPlayerExample2 player = new AudioPlayerExample2();
        player.play(audioFilePath);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int screenWidth = screenSize.width;
        int screenHeight = screenSize.height;

        JFrame f = new JFrame("Z-Order Layer System");
        f.setSize(screenWidth, screenHeight);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        canvas = new CurveCanvas();
        canvas.addMouseListener(new MyProgram());
        canvas.addMouseWheelListener(new MyProgram());
        canvas.setFocusable(true);
        canvas.requestFocusInWindow();
        canvas.addKeyListener(new MyProgram());
        canvas.setBackground(new Color(30, 30, 30));
        f.add(canvas);
        f.setVisible(true);
        
        // 1. Create the TOP layer first (Priority 10)
        // These are the lines you already had.
        String topLayer = "FOREGROUND_LINES";
        canvas.createLayer(topLayer, 10);

        // Draw your original curves here
        int spacing = screenHeight / 84;
        for (int i = spacing; i < screenHeight; i += spacing) {
            addCurvedLine(topLayer, 0, i, screenWidth / 2, i + (i - screenHeight / 2) * 0.3, screenWidth, i, new Color(60, 60, 60, 100));
            
        }

        // 2. Create the BOTTOM layer LATER in the code (Priority 0)
        // Even though we add this second, it will draw UNDER the lines.
        String botLayer = "BACKGROUND_SHAPES";
        canvas.createLayer(botLayer, 0); 
        
        String chosenSplash = null;
        try
        {
        File splash = new File("splash.txt");
        Scanner splashReader = new Scanner(splash);
        
        ArrayList<String> splashes = new ArrayList<String>();
        
        while (splashReader.hasNextLine())
        {
            splashes.add(splashReader.nextLine());
        }
        
        splashReader.close();
        
        Random rand = new Random();
        chosenSplash = splashes.get(rand.nextInt(splashes.size()));
        }
        catch (IOException e)
        {
            System.out.println("Error with the splash text");
        }
        
        try {
            //Thread.sleep(100);
        textMaker.placeText("INITIALIZING SYSTEM...", 20, 15, Color.GREEN);
        Thread.sleep(2400);
        textMaker.placeText("- " + chosenSplash, 20, 15, Color.WHITE);
        textMaker.placeText("----------------------", 0, 15, Color.WHITE);
        Thread.sleep(2000);
        textMaker.placeText("Select a file to open: ", 20, 15, Color.WHITE);
        textMaker.placeText("[-] ProjectDream.iso", 20, 15, Color.WHITE);
        textMaker.placeText("[-] settings.bin", 20, 15, Color.WHITE);
        textMaker.placeText("[-] credits.txt", 20, 15, Color.WHITE);
        Thread.sleep(3500);
        while (true)
        {
        triggerInputPrompt();
        while (isWaitingForInput) {
            Thread.sleep(50); // Pause for 50 milliseconds, then check again
        }
        
        // The code will only reach this point AFTER they hit Enter
        if (currentInput.toLowerCase().equals("projectdream.iso")) {
            // should the loading bar go here?
            textMaker.placeText(" ", 20, 15, Color.WHITE);
            textMaker.placeText("----------------------", 1, 15, Color.WHITE);
            textMaker.placeText("Hello friend :)", 20, 15, Color.WHITE);
            Thread.sleep(2000);
            textMaker.placeText("Thank you for choosing to help participate in the testing for the. . .", 20, 15, Color.WHITE);
                        Thread.sleep(2000);
            textMaker.placeText("Digital Recreation of Emotional Aptitude Model", 20, 17, Color.CYAN);
                        Thread.sleep(2000);
            textMaker.placeText("the Eris Digital company appreciates your help", 20, 15, Color.WHITE);
                        Thread.sleep(2000);
            textMaker.placeText("My job is to keep you company and entertained!", 20, 15, Color.WHITE);
                        Thread.sleep(2000);
            textMaker.placeText("Think of me as your personal digital friend :D", 20, 15, Color.WHITE);
                        Thread.sleep(2000);
            textMaker.placeText("lets start off with the simple stuff, whats your name?", 20, 15, Color.WHITE);
                        Thread.sleep(2000);
                        
                        while (true)
                        {
            triggerInputPrompt();
            while (isWaitingForInput) {
                Thread.sleep(50); // Pause for 50 milliseconds, then check again
                }
                if (currentInput == null || currentInput.trim() == "" || currentInput.length() < 2 ||currentInput.toLowerCase() == "eris")
                {
                    textMaker.placeText("input a valid name", 20, 15, Color.RED);
                } else
                {
                    break;
                }
                        }
            username = currentInput;
                Thread.sleep(1000);
            textMaker.placeText(username + ", huh?", 20, 15, Color.WHITE);
                        Thread.sleep(2000);
            textMaker.placeText("I like that name!", 20, 15, Color.WHITE);
                        Thread.sleep(2000);
            textMaker.placeText("Now I need a name. . . ", 20, 15, Color.WHITE);
            Thread.sleep(2000);
            textMaker.placeText("what should I be called?", 20, 15, Color.WHITE);
                    Thread.sleep(2000);
                     while (true)
                        {
            triggerInputPrompt();
            while (isWaitingForInput) {
                Thread.sleep(50); // Pause for 50 milliseconds, then check again
                }
                if (currentInput == null || currentInput.trim() == "" || currentInput.toLowerCase() == "eris")
                {
                    textMaker.placeText("input a valid name", 20, 15, Color.RED);
                } else
                {
                    break;
                }
                    }
            CPUname = currentInput;
            Thread.sleep(2000);
            textMaker.placeText(CPUname + "?", 200, 15, Color.WHITE);
            Thread.sleep(3000);
            textMaker.placeText(". . .", 500, 15, Color.WHITE);
            Thread.sleep(3000);
            textMaker.placeText("thats nice.", 500, 15, Color.WHITE);
            Thread.sleep(2000);
            textMaker.placeText("Okay now on to the fun part!", 20, 15, Color.WHITE);
        } else if (currentInput.toLowerCase().equals("settings.bin")) {
            System.out.println("opens setting options..");
        } else if (currentInput.toLowerCase().equals("credits.txt")) {
            textMaker.placeText("----------------------", 1, 15, Color.WHITE);
            textMaker.placeText("Developed and Written by: ", 100, 20, Color.WHITE);
            Color PURPLE = new Color(141, 20, 222); 
            textMaker.placeText("Aedan Alfonso (Aesfo)", 1, 35, PURPLE);
            textMaker.placeText(" ", 1 , 45, Color.WHITE);
            textMaker.placeText("Special thanks to my parents for inspiring my love for creative storytelling and computers", 100, 20, Color.WHITE);
            textMaker.placeText("to my friends and family, for always encouraging me :D", 100, 20, Color.WHITE);
            textMaker.placeText("to my girlfriend, who believed in my ability to make something before ever once seeing anything I had made, I love you btw <3", 100, 20, Color.WHITE);
            textMaker.placeText("to you, for playing my silly little side project (and hopefully liking it)", 100, 20, Color.WHITE);
            textMaker.placeText("type BACK to go to the main menu.", 50, 20, Color.RED);
            Thread.sleep(40000);
            while (true)
                        {
            triggerInputPrompt();
            while (isWaitingForInput) {
                Thread.sleep(50); // Pause for 50 milliseconds, then check again
                }
                if (currentInput.toLowerCase() == "exit" || currentInput.toLowerCase() == "back" || currentInput.toLowerCase() == "return" || currentInput.toLowerCase() == "home")
                {
                    textMaker.placeText("Select a file to open: ", 20, 15, Color.WHITE);
        textMaker.placeText("[-] ProjectDream.iso", 20, 15, Color.WHITE);
        textMaker.placeText("[-] settings.bin", 20, 15, Color.WHITE);
        textMaker.placeText("[-] credits.txt", 20, 15, Color.WHITE);
        Thread.sleep(3500);
                } else
                {
                    
                    break;
                }
                        }
            
        } else {
            textMaker.placeText("please select a valid file name", 20, 15, Color.RED);
        }
        }
    } catch (InterruptedException e)
        {
            System.err.println("Something interrupted the main thread, restarting will likely fix the issue");
        }
    }
   
    public static void triggerInputPrompt() {
    isWaitingForInput = true;
    currentInput = "";
    
    for (TextData line : textMaker.activeLines) {
        line.setY(line.y - 40); 
    }
    
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    int startY = (screenSize.height - 100) + globalScrollOffset;
    
    Font activeFont = new Font("SansSerif", Font.BOLD, 15);
    if (!textMaker.activeLines.isEmpty()) {
        activeFont = textMaker.activeLines.get(textMaker.activeLines.size() - 1).font;
    }
    
    inputLineData = new TextData(promptString, 50, startY, 15, Color.GREEN, activeFont);
    textMaker.activeLines.add(inputLineData);
    
    if (canvas != null) {
        canvas.addTextToLayer("BACKGROUND_SHAPES", inputLineData);
        canvas.repaint();
    }
}

public static void addText(String layerName, String text, int x, int y, int size, Color color) {
    if (canvas != null) {
        // Create a default font for standard text additions
        Font defaultFont = new Font("SansSerif", Font.BOLD, size);
        
        canvas.addTextToLayer(layerName, new TextData(text, x, y, size, color, defaultFont));
    }
}
    
 @Override
public void mouseWheelMoved(MouseWheelEvent e) {
    
    
    int scrollSpeed = 25; 
    int scrollAmount = -e.getWheelRotation() * scrollSpeed;

    if (canvas != null) {
        globalScrollOffset += scrollAmount; 
        
        canvas.scrollAllText(scrollAmount);
    }
}

    @Override
public void keyPressed(KeyEvent e) {
    if (isWaitingForInput && inputLineData != null) {
        if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            // Delete the last character if there's anything to delete
            if (currentInput.length() > 0) {
                currentInput = currentInput.substring(0, currentInput.length() - 1);
                inputLineData.setText(promptString + currentInput);
                canvas.repaint();
            }
        } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            // Lock the input system
            isWaitingForInput = false; 
        }
    }
}

@Override
public void keyTyped(KeyEvent e) {
    if (isWaitingForInput && inputLineData != null) {
        char c = e.getKeyChar();
        
        // Only allow standard printable keyboard characters (ASCII 32 through 126)
        if (c >= 32 && c <= 126) {
            currentInput += c;
            inputLineData.setText(promptString + currentInput);
            canvas.repaint();
        }
    }
}
        
        @Override
    public void keyReleased(KeyEvent e) {
        
    }
    
public void mouseClicked(MouseEvent m)
{
 int x = m.getX();
 int y = m.getY();
 String str = "x =" +x+",y = "+y;
 System.out.println(str);
 //showStatus(str);
}

    @Override
    public void mousePressed(MouseEvent e) {
    
}

    @Override
    public void mouseReleased(MouseEvent e) {
       
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        
    }

    @Override
    public void mouseExited(MouseEvent e) {
       System.out.println("Mouse out of bounds");
       
       // refresh screen to fit new size
    }


    public static void addCurvedLine(String layerName, double x1, double y1, double ctrlx, double ctrly, double x2, double y2, Color color) {
        if (canvas != null) {
            canvas.addCurveToLayer(layerName, new CurveData(x1, y1, ctrlx, ctrly, x2, y2, color));
        }
    }

    private static class CurveData {
        QuadCurve2D.Double curve;
        Color color;
        public CurveData(double x1, double y1, double ctrlx, double ctrly, double x2, double y2, Color color) {
            this.curve = new QuadCurve2D.Double(x1, y1, ctrlx, ctrly, x2, y2);
            this.color = color;
        }
    }

    /**
     * Layer now includes a zIndex for stacking order.
     */
    private static class Layer {
        String name;
        int zIndex;
        List<CurveData> curves = new ArrayList<>();
        List<TextData> textItems = new ArrayList<>();
        boolean visible = true;

        public Layer(String name, int zIndex) {
            this.name = name;
            this.zIndex = zIndex;
        }
    }
        
// MUST be public static so textMaker can see it
public static class TextData {
    public String text;
    public int x;        
    public int y;        
    public int size;     
    public Color color;  
    public Font font;

    // Update the constructor to require the font
    public TextData(String text, int x, int y, int size, Color color, Font font) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.size = size;
        this.color = color;
        this.font = font; // Save it
    }

    // Helper methods
    public void setText(String text) { this.text = text; }
    public void setY(int y) { this.y = y; }
}

    public static class CurveCanvas extends JPanel {
        private final Map<String, Layer> layerMap = new HashMap<>();
        // A list we can sort for rendering
        private final List<Layer> renderOrder = new ArrayList<>();

        public void createLayer(String name, int zIndex) {
            if (!layerMap.containsKey(name)) {
                Layer newLayer = new Layer(name, zIndex);
                layerMap.put(name, newLayer);
                renderOrder.add(newLayer);
                
                // Sort layers: lower zIndex first, higher zIndex last
                renderOrder.sort(Comparator.comparingInt(l -> l.zIndex));
            }
        }

        public void addCurveToLayer(String layerName, CurveData curve) {
            Layer layer = layerMap.get(layerName);
            if (layer != null) {
                layer.curves.add(curve);
                repaint();
            }
        }
        
        public void scrollAllText(int scrollAmount) {
    // Loop through every layer
    for (Layer layer : renderOrder) {
        // Loop through every text item in that layer and shift its Y position
        for (TextData td : layer.textItems) {
            td.setY(td.y + scrollAmount);
        }
    }
    repaint(); // Force the canvas to redraw everything at their new positions
}
        
    // Inside MyProgram.java -> CurveCanvas class
public void addTextToLayer(String layerName, TextData text) {
    Layer layer = layerMap.get(layerName);
    if (layer != null) {
        layer.textItems.add(text);
        repaint();
    } else {
        System.out.println("Error: Layer " + layerName + " does not exist!");
    }
}

@Override
protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g;

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    
    g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    int screenWidth = getWidth();
    int screenHeight = getHeight();

    for (Layer layer : renderOrder) {
        if (layer.visible) {
            for (TextData td : layer.textItems) {
    g2.setColor(td.color);

    g2.setFont(td.font); 
    FontMetrics metrics = g2.getFontMetrics(td.font);

                double currentX = td.x; 
                
                // Calculate how far down the screen this entire line of text is (0.0 to 1.0)
                double tY = td.y / (double) screenHeight; 
                
                for (char c : td.text.toCharArray()) {
                    // Calculate how far across the screen this specific character is
                    double tX = currentX / (double) screenWidth;

                    double yBendFactor = (td.y - screenHeight / 2.0) * 0.325;
                    double distortedY = td.y + (2 * (1 - tX) * tX * yBendFactor);
                    

                    double xBendFactor = (currentX - screenWidth / 2.0) * 0.2;
                    double distortedX = currentX + (2 * (1 - tY) * tY * xBendFactor);

                    // Angle calculation for the text rotation
                    double slope = (2 * yBendFactor * (1 - 2 * tX)) / screenWidth;
                    
                    double angle = Math.atan(slope);
                    if (td.y < (screenHeight / 2))
                    {
                    angle =- angle * .90; // sets to negative angle, very lazy way to do this
                    } else
                    {
                        angle = angle * -1;
                    }
                    

                    java.awt.geom.AffineTransform old = g2.getTransform();

                    g2.translate(distortedX, distortedY);
                    g2.rotate(angle);
                    g2.drawString(String.valueOf(c), 0, 0);
                    g2.setTransform(old);

                    currentX += metrics.getStringBounds(String.valueOf(c), g2).getWidth(); 
                }
            }
            }

            // --- Draw Curves ---
            for (CurveData curveData : layer.curves) {
                g2.setColor(curveData.color);
                g2.setStroke(new BasicStroke(2));
                g2.draw(curveData.curve);
            }
        }
    }
}
    }