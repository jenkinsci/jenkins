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

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import jakarta.servlet.ServletOutputStream;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Calendar;
import javax.imageio.ImageIO;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.util.SystemProperties;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.Plot;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;

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
    @Restricted(NoExternalUse.class)
    /* package for test */ static /* non-final for script console */ int MAX_AREA = SystemProperties.getInteger(Graph.class.getName() + ".maxArea", 10_000_000); // 4k*2.5k

    private final long timestamp;
    private final int defaultWidth;
    private final int defaultHeight;
    private final int defaultScale = 1;
    private volatile JFreeChart graph;

    /**
     * @param timestamp
     *      Timestamp of this graph. Used for HTTP cache related headers.
     *      If the graph doesn't have any timestamp to tie it to, pass -1.
     */
    protected Graph(long timestamp, int defaultWidth, int defaultHeight) {
        this.timestamp = timestamp;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
    }

    protected Graph(Calendar timestamp, int defaultWidth, int defaultHeight) {
        this(timestamp.getTimeInMillis(), defaultWidth, defaultHeight);
    }

    /**
     * Actually render a chart.
     */
    protected abstract JFreeChart createGraph();

    private BufferedImage render(StaplerRequest2 req, ChartRenderingInfo info) {
        String w = req.getParameter("width");
        if (w == null) {
            w = String.valueOf(defaultWidth);
        }

        String h = req.getParameter("height");
        if (h == null) {
            h = String.valueOf(defaultHeight);
        }

        String s = req.getParameter("scale");
        if (s == null) {
            s = String.valueOf(defaultScale);
        }

        Color graphBg = stringToColor(req.getParameter("graphBg"));
        Color plotBg = stringToColor(req.getParameter("plotBg"));

        if (graph == null)    graph = createGraph();
        graph.setBackgroundPaint(graphBg);
        Plot p = graph.getPlot();
        p.setBackgroundPaint(plotBg);

        int width = Math.min(Integer.parseInt(w), 2560);
        int height = Math.min(Integer.parseInt(h), 1440);
        int scale = Math.min(Integer.parseInt(s), 3);
        Dimension safeDimension = safeDimension(width, height, defaultWidth, defaultHeight);
        return graph.createBufferedImage(safeDimension.width * scale, safeDimension.height * scale,
                safeDimension.width, safeDimension.height, info);
    }

    @Restricted(NoExternalUse.class)
    @VisibleForTesting
    public static Dimension safeDimension(int width, int height, int defaultWidth, int defaultHeight) {
        if (width <= 0 || height <= 0 || width > MAX_AREA / height) {
            width = defaultWidth;
            height = defaultHeight;
        }
        return new Dimension(width, height);
    }

    @NonNull private static Color stringToColor(@CheckForNull String s) {
        if (s != null) {
            try {
                return Color.decode("0x" + s);
            } catch (NumberFormatException e) {
                return Color.WHITE;
            }
        } else {
            return Color.WHITE;
        }
    }

    /**
     * Renders a graph.
     *
     * @since 2.475
     */
    public void doPng(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (Util.isOverridden(Graph.class, getClass(), "doPng", StaplerRequest.class, StaplerResponse.class)) {
            doPng(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
        } else {
            doPngImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #doPng(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doPng(StaplerRequest req, StaplerResponse rsp) throws IOException {
        doPngImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    private void doPngImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (req.checkIfModified(timestamp, rsp)) return;

        try {
            BufferedImage image = render(req, null);
            rsp.setContentType("image/png");
            ServletOutputStream os = rsp.getOutputStream();
            ImageIO.write(image, "PNG", os);
            os.close();
        } catch (Error e) {
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
            if (e.getMessage().contains("Probable fatal error:No fonts found")) {
                rsp.sendRedirect2(req.getContextPath() + "/images/headless.png");
                return;
            }
            throw e; // otherwise let the caller deal with it
        } catch (HeadlessException e) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath() + "/images/headless.png");
        }
    }

    /**
     * Renders a clickable map.
     *
     * @since 2.475
     */
    public void doMap(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (Util.isOverridden(Graph.class, getClass(), "doMap", StaplerRequest.class, StaplerResponse.class)) {
            doMap(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
        } else {
            doMapImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #doMap(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doMap(StaplerRequest req, StaplerResponse rsp) throws IOException {
        doMapImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    private void doMapImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (req.checkIfModified(timestamp, rsp)) return;

        ChartRenderingInfo info = new ChartRenderingInfo();
        render(req, info);

        rsp.setContentType("text/plain;charset=UTF-8");
        rsp.getWriter().println(ChartUtilities.getImageMap("map", info));
    }
}
