/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.ChartUtilities;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletOutputStream;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Calendar;
import java.awt.image.BufferedImage;
import java.awt.*;

/**
 * A JFreeChart-generated graph that's bound to UI.
 *
 * <p>
 * This object exposes two URLs:
 * <dl>
 * <dt>/png
 * <dd>PNG image of a graph
 *
 * <dt>/map
 * <dd>Clickable map
 * </dl>
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.320
 */
public abstract class Graph {
    private final long timestamp;
    private final int defaultW;
    private final int defaultH;
    private volatile JFreeChart graph;

    /**
     * @param timestamp
     *      Timestamp of this graph. Used for HTTP cache related headers.
     *      If the graph doesn't have any timestamp to tie it to, pass -1.
     */
    protected Graph(long timestamp, int defaultW, int defaultH) {
        this.timestamp = timestamp;
        this.defaultW = defaultW;
        this.defaultH = defaultH;
    }

    protected Graph(Calendar timestamp, int defaultW, int defaultH) {
        this(timestamp.getTimeInMillis(),defaultW,defaultH);
    }

    /**
     * Actually render a chart.
     */
    protected abstract JFreeChart createGraph();

    private BufferedImage render(StaplerRequest req, ChartRenderingInfo info) {
        String w = req.getParameter("width");
        if(w==null)     w=String.valueOf(defaultW);
        String h = req.getParameter("height");
        if(h==null)     h=String.valueOf(defaultH);

        if (graph==null)    graph = createGraph();
        return graph.createBufferedImage(Integer.parseInt(w),Integer.parseInt(h),info);
    }

    /**
     * Renders a graph.
     */
    public void doPng(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (req.checkIfModified(timestamp, rsp)) return;

        try {
            BufferedImage image = render(req,null);
            rsp.setContentType("image/png");
            ServletOutputStream os = rsp.getOutputStream();
            ImageIO.write(image, "PNG", os);
            os.close();
        } catch(Error e) {
            /* OpenJDK on ARM produces an error like this in case of headless error
                Caused by: java.lang.Error: Probable fatal error:No fonts found.
                        at sun.font.FontManager.getDefaultPhysicalFont(FontManager.java:1088)
                        at sun.font.FontManager.initialiseDeferredFont(FontManager.java:967)
                        at sun.font.CompositeFont.doDeferredInitialisation(CompositeFont.java:254)
                        at sun.font.CompositeFont.getSlotFont(CompositeFont.java:334)
                        at sun.font.CompositeStrike.getStrikeForSlot(CompositeStrike.java:77)
                        at sun.font.CompositeStrike.getFontMetrics(CompositeStrike.java:93)
                        at sun.font.Font2D.getFontMetrics(Font2D.java:387)
                        at java.awt.Font.defaultLineMetrics(Font.java:2082)
                        at java.awt.Font.getLineMetrics(Font.java:2152)
                        at org.jfree.chart.axis.NumberAxis.estimateMaximumTickLabelHeight(NumberAxis.java:974)
                        at org.jfree.chart.axis.NumberAxis.selectVerticalAutoTickUnit(NumberAxis.java:1104)
                        at org.jfree.chart.axis.NumberAxis.selectAutoTickUnit(NumberAxis.java:1048)
                        at org.jfree.chart.axis.NumberAxis.refreshTicksVertical(NumberAxis.java:1249)
                        at org.jfree.chart.axis.NumberAxis.refreshTicks(NumberAxis.java:1149)
                        at org.jfree.chart.axis.ValueAxis.reserveSpace(ValueAxis.java:788)
                        at org.jfree.chart.plot.CategoryPlot.calculateRangeAxisSpace(CategoryPlot.java:2650)
                        at org.jfree.chart.plot.CategoryPlot.calculateAxisSpace(CategoryPlot.java:2669)
                        at org.jfree.chart.plot.CategoryPlot.draw(CategoryPlot.java:2716)
                        at org.jfree.chart.JFreeChart.draw(JFreeChart.java:1222)
                        at org.jfree.chart.JFreeChart.createBufferedImage(JFreeChart.java:1396)
                        at org.jfree.chart.JFreeChart.createBufferedImage(JFreeChart.java:1376)
                        at org.jfree.chart.JFreeChart.createBufferedImage(JFreeChart.java:1361)
                        at hudson.util.ChartUtil.generateGraph(ChartUtil.java:116)
                        at hudson.util.ChartUtil.generateGraph(ChartUtil.java:99)
                        at hudson.tasks.test.AbstractTestResultAction.doPng(AbstractTestResultAction.java:196)
                        at hudson.tasks.test.TestResultProjectAction.doTrend(TestResultProjectAction.java:97)
                        ... 37 more
             */
            if(e.getMessage().contains("Probable fatal error:No fonts found")) {
                rsp.sendRedirect2(req.getContextPath()+"/images/headless.png");
                return;
            }
            throw e; // otherwise let the caller deal with it
        } catch(HeadlessException e) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath()+"/images/headless.png");
        }
    }

    /**
     * Renders a clickable map.
     */
    public void doMap(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (req.checkIfModified(timestamp, rsp)) return;

        ChartRenderingInfo info = new ChartRenderingInfo();
        render(req,info);

        rsp.setContentType("text/plain;charset=UTF-8");
        rsp.getWriter().println(ChartUtilities.getImageMap( "map", info ));
    }
}
