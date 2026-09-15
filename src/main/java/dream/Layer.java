package dream;

import java.util.ArrayList;
import java.util.List;

/** A named draw group. Lower zIndex values paint first, so they sit underneath. */
public class Layer {

    public final String name;
    public final int zIndex;
    public final List<CurveData> curves = new ArrayList<>();
    public final List<TextData> textItems = new ArrayList<>();
    public boolean visible = true;

    public Layer(String name, int zIndex) {
        this.name = name;
        this.zIndex = zIndex;
    }
}
