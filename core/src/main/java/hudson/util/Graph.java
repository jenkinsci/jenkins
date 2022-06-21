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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.HeadlessException;
import java.awt.Paint;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Build;
import hudson.model.Job;
import hudson.model.Messages;
import hudson.model.MultiStageTimeSeries;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.util.SystemProperties;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

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
public class Graph {
    public interface GraphSource {

        String getRowKey(int j);

        String getColumnKey(int i);

        float[] getDataPoints(int i);

        int getColumnCount();

        Result getColor(int i);

        String getTooltip(int column, int row);
    }

    private static final Font CHART_FONT = Font.getFont(MultiStageTimeSeries.class.getName() + ".chartFont",
            new Font(Font.SANS_SERIF, Font.PLAIN, 10));

    @Restricted(NoExternalUse.class)
    /* package for test */ static /* non-final for script console */ int MAX_AREA = SystemProperties.getInteger(Graph.class.getName() + ".maxArea", 10_000_000); // 4k*2.5k

    private final long timestamp;
    private final int defaultWidth;
    private final int defaultHeight;
    private final int defaultScale = 1;
    private volatile JFreeChart graph;
    private GraphSource source;

    /**
     * @param timestamp
     *      Timestamp of this graph. Used for HTTP cache related headers.
     *      If the graph doesn't have any timestamp to tie it to, pass -1.
     */
    public Graph(long timestamp, int defaultWidth, int defaultHeight, GraphSource source) {
        this.timestamp = timestamp;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.source = source;
    }

    protected Graph(long timestamp, int defaultWidth, int defaultHeight) {
        this(timestamp, defaultWidth, defaultHeight, null);
    }

    protected Graph(Calendar timestamp, int defaultWidth, int defaultHeight) {
        this(timestamp.getTimeInMillis(), defaultWidth, defaultHeight);
    }

    /**
     * Actually render a chart.
     */
    protected JFreeChart createGraph() {
        final JFreeChart chart = ChartFactory.createLineChart(null, // chart title
                null, // unused
                null, // range axis label
                createDataset(), // data
                PlotOrientation.VERTICAL, // orientation
                true, // include legend
                true, // tooltips
                false // urls
        );

        chart.setBackgroundPaint(Color.white);
        chart.getLegend().setItemFont(CHART_FONT);

        final CategoryPlot plot = chart.getCategoryPlot();
        configurePlot(plot);

        configureRangeAxis((NumberAxis) plot.getRangeAxis());

        crop(plot);

        return chart;
    }

    protected JFreeChart createJobGraph(GraphSource source) {
        DataSetBuilder<String, ChartLabel> data = new DataSetBuilder<>();
        for (int row = 0; row < source.getDataPoints(0).length; row++) {
            data.add(source.getDataPoints(0)[row], "min",
                    new ChartLabel(row, source.getColor(row),
                            source.getRowKey(row),
                            source.getTooltip(0, row)));
        }

        final CategoryDataset dataset = data.build();

        final JFreeChart chart = ChartFactory.createStackedAreaChart(null, // chart
                // title
                null, // unused
                Messages.Job_minutes(), // range axis label
                dataset, // data
                PlotOrientation.VERTICAL, // orientation
                false, // include legend
                true, // tooltips
                false // urls
        );

        chart.setBackgroundPaint(Color.white);

        final CategoryPlot plot = chart.getCategoryPlot();

        // plot.setAxisOffset(new Spacer(Spacer.ABSOLUTE, 5.0, 5.0, 5.0, 5.0));
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(null);
        plot.setForegroundAlpha(0.8f);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.black);

        CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
        plot.setDomainAxis(domainAxis);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.0);
        domainAxis.setCategoryMargin(0.0);

        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        ChartUtil.adjustChebyshev(dataset, rangeAxis);
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        StackedAreaRenderer ar = new ChartLabelStackedAreaRenderer2(dataset);
        plot.setRenderer(ar);

        // crop extra space around the graph
        plot.setInsets(new RectangleInsets(0, 0, 0, 5.0));

        return chart;

    }



    @SuppressFBWarnings(value = "EQ_DOESNT_OVERRIDE_EQUALS", justification = "category dataset is only relevant for coloring, not equality")
    private static class ChartLabelStackedAreaRenderer2 extends StackedAreaRenderer2 {
        private final CategoryDataset categoryDataset;

        ChartLabelStackedAreaRenderer2(CategoryDataset categoryDataset) {
            this.categoryDataset = categoryDataset;
        }

        @Override
        public Paint getItemPaint(int row, int column) {
            ChartLabel key = (ChartLabel) categoryDataset.getColumnKey(column);
            return getColor(key.runResult);
        }

        @Override
        public String generateURL(CategoryDataset dataset, int row, int column) {
            ChartLabel label = (ChartLabel) dataset.getColumnKey(column);
            return String.valueOf(label.runNumber);
        }

        @Override
        public String generateToolTip(CategoryDataset dataset, int row, int column) {
            ChartLabel label = (ChartLabel) dataset.getColumnKey(column);
            return label.tooltip;
        }
    }

    public static Color getColor(Result r) {
        if (r == Result.FAILURE)
            return ColorPalette.RED;
        else if (r == Result.UNSTABLE)
            return ColorPalette.YELLOW;
        else if (r == Result.ABORTED || r == Result.NOT_BUILT)
            return ColorPalette.DARK_GREY;
        else
            return ColorPalette.BLUE;
    }

    private static class ChartLabel implements Comparable<ChartLabel> {
        final int runNumber;
        final Result runResult;
        final String tooltip;
        final String label;

        ChartLabel(int runNumber, Result result, String tooltip, String label) {
            this.runNumber = runNumber;
            this.runResult = result;
            this.tooltip = tooltip;
            this.label = label;
        }

        @Override
        public int compareTo(ChartLabel that) {
            return this.runNumber - that.runNumber;
        }

        @Override
        public boolean equals(Object o) {
            // JENKINS-2682 workaround for Eclipse compilation bug
            // on (c instanceof ChartLabel)
            if (o == null || !ChartLabel.class.isAssignableFrom(o.getClass()))  {
                return false;
            }
            ChartLabel that = (ChartLabel) o;
            return runNumber == that.runNumber;
        }

        @Override
        public int hashCode() {
            return runNumber;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Creates a {@link DefaultCategoryDataset} for rendering a graph from a set of {@link MultiStageTimeSeries}.
     */
    protected DefaultCategoryDataset createDataset() {
        float[][] dataPoints = new float[source.getColumnCount()][];
        for (int i = 0; i < source.getColumnCount(); i++)
            dataPoints[i] = source.getDataPoints(i);

        int dataLength = dataPoints[0].length;
        for (float[] dataPoint : dataPoints)
            assert dataLength == dataPoint.length;

        DefaultCategoryDataset ds = new DefaultCategoryDataset();

        for (int i = dataLength - 1; i >= 0; i--) {
            for (int j = 0; j < dataPoints.length; j++)
                ds.addValue(dataPoints[j][i], source.getRowKey(j), source.getColumnKey(i));
        }
        return ds;
    }

    protected void configureRangeAxis(NumberAxis rangeAxis) {
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        rangeAxis.setTickLabelFont(CHART_FONT);
        rangeAxis.setLabelFont(CHART_FONT);
    }

    protected void configureRenderer(LineAndShapeRenderer renderer) {
        renderer.setBaseStroke(new BasicStroke(3));

        for (int i = 0; i < source.getColumnCount(); i++)
            renderer.setSeriesPaint(i, getColor(source.getColor(i)));
    }

    protected void configurePlot(CategoryPlot plot) {
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(null);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.black);

        configureRenderer((LineAndShapeRenderer) plot.getRenderer());
        configureDomainAxis(plot);
    }

    protected CategoryAxis configureDomainAxis(CategoryPlot plot) {
        final CategoryAxis domainAxis = new NoOverlapCategoryAxis(null);
        plot.setDomainAxis(domainAxis);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.0);
        domainAxis.setCategoryMargin(0.0);
        domainAxis.setLabelFont(CHART_FONT);
        domainAxis.setTickLabelFont(CHART_FONT);
        return domainAxis;
    }

    protected void crop(CategoryPlot plot) {
        // crop extra space around the graph
        plot.setInsets(new RectangleInsets(0, 0, 0, 5.0));
    }

    private BufferedImage render(StaplerRequest req, ChartRenderingInfo info) {
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
        return renderGraph(safeDimension, scale, info);
    }

    public BufferedImage renderGraph(Dimension safeDimension, int scale, ChartRenderingInfo info) {
        if (graph == null) {
            graph = createGraph();
        }
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
     */
    public void doPng(StaplerRequest req, StaplerResponse rsp) throws IOException {
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
     */
    public void doMap(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (req.checkIfModified(timestamp, rsp)) return;

        ChartRenderingInfo info = new ChartRenderingInfo();
        render(req, info);

        rsp.setContentType("text/plain;charset=UTF-8");
        rsp.getWriter().println(ChartUtilities.getImageMap("map", info));
    }
}
