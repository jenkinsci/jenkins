package hudson.util;

import org.jfree.chart.renderer.category.LineAndShapeRenderer;

import java.awt.Color;
import java.util.List;
import java.util.Collections;
import java.util.Arrays;

/**
 * Color constants consistent with the Hudson color palette. 
 *
 * @author Kohsuke Kawaguchi
 */
public class ColorPalette {
    public static final Color RED = new Color(0xEF,0x29,0x29);
    public static final Color YELLOW = new Color(0xFC,0xE9,0x4F);
    public static final Color BLUE = new Color(0x72,0x9F,0xCF);

    /**
     * Color list usable for generating line charts.
     */
    public static List<Color> LINE_GRAPH = Collections.unmodifiableList(Arrays.asList(
        new Color(0xCC0000),
        new Color(0x3465a4),
        new Color(0x73d216),
        new Color(0xedd400)
    ));

    /**
     * Applies {@link #LINE_GRAPH} colors to the given renderer.
     */
    public static void apply(LineAndShapeRenderer renderer) {
        int n=0;
        for (Color c : LINE_GRAPH)
            renderer.setSeriesPaint(n++,c);
    }
}
