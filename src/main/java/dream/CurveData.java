package dream;

import java.awt.Color;
import java.awt.geom.QuadCurve2D;

/** One of the curved scanlines that give the screen its bulged-glass look. */
public class CurveData {

    public final QuadCurve2D.Double curve;
    public final Color color;

    public CurveData(double x1, double y1, double ctrlx, double ctrly,
                     double x2, double y2, Color color) {
        this.curve = new QuadCurve2D.Double(x1, y1, ctrlx, ctrly, x2, y2);
        this.color = color;
    }
}
