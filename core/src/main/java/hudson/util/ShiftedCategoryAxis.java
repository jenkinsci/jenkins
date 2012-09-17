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

import org.jfree.chart.axis.CategoryAxis;
import org.jfree.ui.RectangleEdge;

import java.awt.geom.Rectangle2D;

/**
 * {@link CategoryAxis} shifted to left to eliminate redundant space
 * between area and the Y-axis.
 */
public final class ShiftedCategoryAxis extends NoOverlapCategoryAxis {
    public ShiftedCategoryAxis(String label) {
        super(label);
    }

    @Override
    protected double calculateCategorySize(int categoryCount, Rectangle2D area, RectangleEdge edge) {
        // we cut the left-half of the first item and the right-half of the last item,
        // so we have more space
        return super.calculateCategorySize(categoryCount-1, area, edge);
    }

    @Override
    public double getCategoryEnd(int category, int categoryCount, Rectangle2D area, RectangleEdge edge) {
        return super.getCategoryStart(category, categoryCount, area, edge)
           + calculateCategorySize(categoryCount, area, edge) / 2;
    }

    @Override
    public double getCategoryMiddle(int category, int categoryCount, Rectangle2D area, RectangleEdge edge) {
        return super.getCategoryStart(category, categoryCount, area, edge);
    }

    @Override
    public double getCategoryStart(int category, int categoryCount, Rectangle2D area, RectangleEdge edge) {
        return super.getCategoryStart(category, categoryCount, area, edge)
            - calculateCategorySize(categoryCount, area, edge) / 2;
    }
}
