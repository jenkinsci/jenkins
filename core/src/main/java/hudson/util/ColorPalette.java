/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
    public static final Color GREY = new Color(0xAB,0xAB,0xAB);
    
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
