/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

import java.awt.Dimension;
import org.junit.Assert;
import org.junit.Test;

public class GraphTest {

    public static final int DEFAULT_W = 400;
    public static final int DEFAULT_H = 300;

    @Test
    public void testDimensions() {
        final Dimension keep = Graph.safeDimension(Graph.MAX_AREA / 1_000, 1000, DEFAULT_W, DEFAULT_H);
        Assert.assertEquals(Graph.MAX_AREA / 1_000, keep.width);
        Assert.assertEquals(1_000, keep.height);

        final Dimension keep2 = Graph.safeDimension(Graph.MAX_AREA / 2, 2, DEFAULT_W, DEFAULT_H);
        Assert.assertEquals(Graph.MAX_AREA / 2, keep2.width);
        Assert.assertEquals(2, keep2.height);

        final Dimension resetArea = Graph.safeDimension(Graph.MAX_AREA, Graph.MAX_AREA, DEFAULT_W, DEFAULT_H);
        Assert.assertEquals(DEFAULT_W, resetArea.width);
        Assert.assertEquals(DEFAULT_H, resetArea.height);

        final Dimension resetNegativeWidth = Graph.safeDimension(-50, 1000, DEFAULT_W, DEFAULT_H);
        Assert.assertEquals(DEFAULT_W, resetNegativeWidth.width);
        Assert.assertEquals(DEFAULT_H, resetNegativeWidth.height);

        final Dimension resetNegativeHeight = Graph.safeDimension(1000, -50, DEFAULT_W, DEFAULT_H);
        Assert.assertEquals(DEFAULT_W, resetNegativeHeight.width);
        Assert.assertEquals(DEFAULT_H, resetNegativeHeight.height);
    }
}
