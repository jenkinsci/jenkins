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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

public class GraphTest {

    public static final int DEFAULT_W = 400;
    public static final int DEFAULT_H = 300;

    @Issue("JENKINS-54319")
    @Test
    void doPngToleratesFontRenderingFailure() throws Exception {
        Graph graph = brokenFontGraph();
        StaplerRequest2 req = mock(StaplerRequest2.class);
        StaplerResponse2 rsp = mock(StaplerResponse2.class);
        when(req.checkIfModified(anyLong(), any())).thenReturn(false);
        when(req.getContextPath()).thenReturn("");

        graph.doPng(req, rsp);

        verify(rsp).sendRedirect2("/images/headless.png");
    }

    @Issue("JENKINS-54319")
    @Test
    void doMapToleratesFontRenderingFailure() throws Exception {
        Graph graph = brokenFontGraph();
        StaplerRequest2 req = mock(StaplerRequest2.class);
        StaplerResponse2 rsp = mock(StaplerResponse2.class);
        when(req.checkIfModified(anyLong(), any())).thenReturn(false);
        StringWriter sw = new StringWriter();
        when(rsp.getWriter()).thenReturn(new PrintWriter(sw));

        graph.doMap(req, rsp);

        assertThat(sw.toString(), not(containsString("Exception")));
    }

    /**
     * A graph whose chart rendering always fails the way JFreeChart/AWT does on a host with a
     * broken font configuration (JENKINS-54319), regardless of the actual system fonts available
     * where the test runs.
     */
    private static Graph brokenFontGraph() {
        return new Graph(-1, 100, 100) {
            @Override
            protected JFreeChart createGraph() {
                JFreeChart real = ChartFactory.createLineChart("t", "x", "y", new DefaultCategoryDataset());
                return new JFreeChart(real.getPlot()) {
                    @Override
                    public BufferedImage createBufferedImage(int width, int height, double widthScaleFactor, double heightScaleFactor, ChartRenderingInfo info) {
                        throw new ArrayIndexOutOfBoundsException(0);
                    }
                };
            }
        };
    }

    @Test
    void testDimensions() {
        final Dimension keep = Graph.safeDimension(Graph.MAX_AREA / 1_000, 1000, DEFAULT_W, DEFAULT_H);
        assertEquals(Graph.MAX_AREA / 1_000, keep.width);
        assertEquals(1_000, keep.height);

        final Dimension keep2 = Graph.safeDimension(Graph.MAX_AREA / 2, 2, DEFAULT_W, DEFAULT_H);
        assertEquals(Graph.MAX_AREA / 2, keep2.width);
        assertEquals(2, keep2.height);

        final Dimension resetArea = Graph.safeDimension(Graph.MAX_AREA, Graph.MAX_AREA, DEFAULT_W, DEFAULT_H);
        assertEquals(DEFAULT_W, resetArea.width);
        assertEquals(DEFAULT_H, resetArea.height);

        final Dimension resetNegativeWidth = Graph.safeDimension(-50, 1000, DEFAULT_W, DEFAULT_H);
        assertEquals(DEFAULT_W, resetNegativeWidth.width);
        assertEquals(DEFAULT_H, resetNegativeWidth.height);

        final Dimension resetNegativeHeight = Graph.safeDimension(1000, -50, DEFAULT_W, DEFAULT_H);
        assertEquals(DEFAULT_W, resetNegativeHeight.width);
        assertEquals(DEFAULT_H, resetNegativeHeight.height);
    }
}
