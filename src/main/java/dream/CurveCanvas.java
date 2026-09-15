package dream;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The CRT screen itself: a stack of named layers holding text and scanlines,
 * drawn with a barrel distortion so the picture looks bowed like old glass.
 *
 * <p>All mutation happens on the Swing event thread.
 */
public class CurveCanvas extends JPanel {

    /** Dialogue and the input prompt live here. */
    public static final String TEXT_LAYER = "TERMINAL_TEXT";

    /** The curved scanlines drawn over the top of everything. */
    public static final String SCANLINE_LAYER = "SCANLINES";

    private static final int SCANLINE_COUNT = 84;

    private final Map<String, Layer> layerMap = new HashMap<>();
    private final List<Layer> renderOrder = new ArrayList<>();

    public CurveCanvas() {
        setBackground(new Color(30, 30, 30));
        setFocusable(true);

        // Text underneath, scanlines on top.
        createLayer(TEXT_LAYER, 0);
        createLayer(SCANLINE_LAYER, 10);

        // Scanlines are sized to the canvas, so rebuild them whenever it changes.
        // The original built them once from the raw screen size, which is why the
        // effect drifted out of alignment at other window sizes.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                rebuildScanlines();
            }
        });
    }

    public void createLayer(String name, int zIndex) {
        if (!layerMap.containsKey(name)) {
            Layer newLayer = new Layer(name, zIndex);
            layerMap.put(name, newLayer);
            renderOrder.add(newLayer);
            renderOrder.sort(Comparator.comparingInt(l -> l.zIndex));
        }
    }

    /** Lays the curved scanlines out to fit the current canvas size. */
    public void rebuildScanlines() {
        Layer layer = layerMap.get(SCANLINE_LAYER);
        if (layer == null) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        layer.curves.clear();
        int spacing = Math.max(1, height / SCANLINE_COUNT);
        Color lineColor = new Color(60, 60, 60, 100);

        for (int y = spacing; y < height; y += spacing) {
            double bow = y + (y - height / 2.0) * 0.3;
            layer.curves.add(new CurveData(0, y, width / 2.0, bow, width, y, lineColor));
        }
        repaint();
    }

    public void addCurveToLayer(String layerName, CurveData curve) {
        Layer layer = layerMap.get(layerName);
        if (layer != null) {
            layer.curves.add(curve);
            repaint();
        }
    }

    public void addTextToLayer(String layerName, TextData text) {
        Layer layer = layerMap.get(layerName);
        if (layer != null) {
            layer.textItems.add(text);
            repaint();
        } else {
            Log.warn("Layer " + layerName + " does not exist.");
        }
    }

    /** Shifts every line of text vertically. Used by the scroll wheel. */
    public void scrollAllText(int scrollAmount) {
        for (Layer layer : renderOrder) {
            for (TextData td : layer.textItems) {
                td.setY(td.y + scrollAmount);
            }
        }
        repaint();
    }

    /** Drops text that has scrolled far enough away to be unreachable. */
    public void pruneText(int keepNewest) {
        Layer layer = layerMap.get(TEXT_LAYER);
        if (layer == null || layer.textItems.size() <= keepNewest) {
            return;
        }
        int excess = layer.textItems.size() - keepNewest;
        layer.textItems.subList(0, excess).clear();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        // Aliased text is deliberate: it keeps the chunky DOS-terminal look.
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
            RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        for (Layer layer : renderOrder) {
            if (!layer.visible) {
                continue;
            }
            drawText(g2, layer, width, height);
            drawCurves(g2, layer);
        }
    }

    /**
     * Draws each line one character at a time, nudging every glyph along a
     * quadratic curve so the whole screen appears to bulge outwards.
     */
    private void drawText(Graphics2D g2, Layer layer, int width, int height) {
        for (TextData td : layer.textItems) {
            // Skip anything scrolled well clear of the viewport. With per-glyph
            // transforms this matters a lot once the log gets long.
            if (td.y < -120 || td.y > height + 120 || td.text.isEmpty()) {
                continue;
            }

            g2.setColor(td.color);
            g2.setFont(td.font);
            FontMetrics metrics = g2.getFontMetrics(td.font);

            double currentX = td.x;
            double tY = td.y / (double) height;

            for (char c : td.text.toCharArray()) {
                double tX = currentX / (double) width;

                double yBendFactor = (td.y - height / 2.0) * 0.325;
                double distortedY = td.y + (2 * (1 - tX) * tX * yBendFactor);

                double xBendFactor = (currentX - width / 2.0) * 0.2;
                double distortedX = currentX + (2 * (1 - tY) * tY * xBendFactor);

                // Tilt each glyph to sit tangent to the curve it rides on.
                double slope = (2 * yBendFactor * (1 - 2 * tX)) / width;
                double angle = Math.atan(slope);
                angle = td.y < height / 2 ? -angle * 0.90 : -angle;

                AffineTransform old = g2.getTransform();
                g2.translate(distortedX, distortedY);
                g2.rotate(angle);
                g2.drawString(String.valueOf(c), 0, 0);
                g2.setTransform(old);

                currentX += metrics.getStringBounds(String.valueOf(c), g2).getWidth();
            }
        }
    }

    private void drawCurves(Graphics2D g2, Layer layer) {
        g2.setStroke(new BasicStroke(2));
        for (CurveData curveData : layer.curves) {
            g2.setColor(curveData.color);
            g2.draw(curveData.curve);
        }
    }
}
