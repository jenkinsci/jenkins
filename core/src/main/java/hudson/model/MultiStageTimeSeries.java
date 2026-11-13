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

package hudson.model;

import hudson.Util;
import hudson.util.ChartUtil;
import hudson.util.ColorPalette;
import hudson.util.NoOverlapCategoryAxis;
import jakarta.servlet.ServletException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Maintains several {@link TimeSeries} with different update frequencies to satisfy three goals;
 * (1) retain data over long timespan, (2) save memory, and (3) retain accurate data for the recent past.
 */
@ExportedBean
public class MultiStageTimeSeries implements Serializable {
    /**
     * Name of this data series.
     */
    public final Localizable title;

    /**
     * Used to render a line in the trend chart.
     */
    public final Color color;

    /**
     * Updated every 10 seconds. Keep data up to 6 hours.
     */
    @Exported
    public final TimeSeries sec10;
    /**
     * Updated every 1 min. Keep data up to 2 days.
     */
    @Exported
    public final TimeSeries min;
    /**
     * Updated every 1 hour. Keep data up to 8 weeks.
     */
    @Exported
    public final TimeSeries hour;

    private int counter;

    private static final Font CHART_FONT = Font.getFont(MultiStageTimeSeries.class.getName() + ".chartFont",
            new Font(Font.SANS_SERIF, Font.PLAIN, 10));

    public MultiStageTimeSeries(Localizable title, Color color, float initialValue, float decay) {
        this.title = title;
        this.color = color;
        this.sec10 = new TimeSeries(initialValue, decay, 6 * (int) TimeUnit.HOURS.toMinutes(6));
        this.min = new TimeSeries(initialValue, decay, (int) TimeUnit.DAYS.toMinutes(2));
        this.hour = new TimeSeries(initialValue, decay, (int) TimeUnit.DAYS.toHours(56));
    }

    /**
     * @deprecated since 2009-04-05.
     *      Use {@link #MultiStageTimeSeries(Localizable, Color, float, float)}
     */
    @Deprecated
    public MultiStageTimeSeries(float initialValue, float decay) {
        this(Messages._MultiStageTimeSeries_EMPTY_STRING(), Color.WHITE, initialValue, decay);
    }

    /**
     * Call this method every 10 sec and supply a new data point.
     */
    public void update(float f) {
        counter = (counter + 1) % 360;   // 1hour/10sec = 60mins/10sec=3600secs/10sec = 360
        sec10.update(f);
        if (counter % 6 == 0)    min.update(f);
        if (counter == 0)      hour.update(f);
    }

    /**
     * Selects a {@link TimeSeries}.
     */
    public TimeSeries pick(TimeScale timeScale) {
        return switch (timeScale) {
            case HOUR -> hour;
            case MIN -> min;
            case SEC10 -> sec10;
            default -> throw new AssertionError();
        };
    }

    /**
     * Gets the most up-to-date data point value.
     */
    public float getLatest(TimeScale timeScale) {
        return pick(timeScale).getLatest();
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Choose which datapoint to use.
     */
    public enum TimeScale {
        SEC10(TimeUnit.SECONDS.toMillis(10)),
        MIN(TimeUnit.MINUTES.toMillis(1)),
        HOUR(TimeUnit.HOURS.toMillis(1));

        /**
         * Number of milliseconds (10 secs, 1 min, and 1 hour)
         * that this constant represents.
         */
        public final long tick;

        TimeScale(long tick) {
            this.tick = tick;
        }

        /**
         * Creates a new {@link DateFormat} suitable for processing
         * this {@link TimeScale}.
         */
        public DateFormat createDateFormat() {
            return switch (this) {
                case HOUR -> new SimpleDateFormat("MMM/dd HH");
                case MIN -> new SimpleDateFormat("E HH:mm");
                case SEC10 -> new SimpleDateFormat("HH:mm:ss");
                default -> throw new AssertionError();
            };
        }

        /**
         * Parses the {@link TimeScale} from the query parameter.
         */
        public static TimeScale parse(String type) {
            if (type == null)   return TimeScale.MIN;
            return Enum.valueOf(TimeScale.class, type.toUpperCase(Locale.ENGLISH));
        }
    }

    /**
     * Represents the trend chart that consists of several {@link MultiStageTimeSeries}.
     *
     * <p>
     * This object is renderable as HTTP response.
     */
    public static class TrendChart implements HttpResponse {
        public final TimeScale timeScale;
        public final List<MultiStageTimeSeries> series;
        public final DefaultCategoryDataset dataset;

        public TrendChart(TimeScale timeScale, MultiStageTimeSeries... series) {
            this.timeScale = timeScale;
            this.series = new ArrayList<>(Arrays.asList(series));
            this.dataset = createDataset();
        }

        /**
         * Creates a {@link DefaultCategoryDataset} for rendering a graph from a set of {@link MultiStageTimeSeries}.
         */
        protected DefaultCategoryDataset createDataset() {
            float[][] dataPoints = new float[series.size()][];
            for (int i = 0; i < series.size(); i++)
                dataPoints[i] = series.get(i).pick(timeScale).getHistory();

            int dataLength = dataPoints[0].length;
            for (float[] dataPoint : dataPoints)
                assert dataLength == dataPoint.length;

            DefaultCategoryDataset ds = new DefaultCategoryDataset();

            DateFormat format = timeScale.createDateFormat();

            Date dt = new Date(System.currentTimeMillis() - timeScale.tick * dataLength);
            for (int i = dataLength - 1; i >= 0; i--) {
                dt = new Date(dt.getTime() + timeScale.tick);
                String l = format.format(dt);
                for (int j = 0; j < dataPoints.length; j++)
                    ds.addValue(dataPoints[j][i], series.get(j).title.toString(), l);
            }
            return ds;
        }

        /**
         * Draws a chart into {@link JFreeChart}.
         */
        public JFreeChart createChart() {
            final JFreeChart chart = ChartFactory.createLineChart(null, // chart title
                    null, // unused
                    null, // range axis label
                    dataset, // data
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

        protected void configureRangeAxis(NumberAxis rangeAxis) {
            rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
            rangeAxis.setTickLabelFont(CHART_FONT);
            rangeAxis.setLabelFont(CHART_FONT);
        }

        protected void crop(CategoryPlot plot) {
            // crop extra space around the graph
            plot.setInsets(new RectangleInsets(0, 0, 0, 5.0));
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

        protected void configureRenderer(LineAndShapeRenderer renderer) {
            renderer.setBaseStroke(new BasicStroke(3));

            for (int i = 0; i < series.size(); i++)
                renderer.setSeriesPaint(i, series.get(i).color);
        }

        protected void configurePlot(CategoryPlot plot) {
            plot.setBackgroundPaint(Color.WHITE);
            plot.setOutlinePaint(null);
            plot.setRangeGridlinesVisible(true);
            plot.setRangeGridlinePaint(Color.black);

            configureRenderer((LineAndShapeRenderer) plot.getRenderer());
            configureDomainAxis(plot);
        }

        /**
         * Renders this trend chart as an inline SVG document.
         *
         * <p>
         * Unlike {@link #generateResponse}, which produces a theme-agnostic PNG via JFreeChart,
         * this builds an SVG string meant to be embedded directly into the page DOM. Line and
         * text colors reference CSS custom properties (e.g. {@code var(--red)}), so the chart
         * adapts to the active theme (such as dark mode) automatically, and the {@code viewBox}
         * lets it scale fluidly with its container instead of being a fixed-size raster.
         */
        public String createSvg() {
            // Logical coordinate system; the on-screen size is controlled by CSS via the viewBox.
            final int width = 1000;
            final int height = 500;
            final double plotRight = width - 12;
            final double plotTop = 32;
            // Keep the plot area roughly its original size (its bottom was at 344 when the chart was
            // 400 tall); the taller canvas devotes the extra height to the bottom margin, which holds
            // the x-axis labels. Those are rotated 90° and so extend downward and need the room.
            final double plotBottom = 435;
            final double plotH = plotBottom - plotTop;

            // Gather the raw data (newest-first) and x-axis labels (oldest -> newest), mirroring createDataset().
            float[][] dataPoints = new float[series.size()][];
            for (int i = 0; i < series.size(); i++)
                dataPoints[i] = series.get(i).pick(timeScale).getHistory();
            int len = dataPoints[0].length;

            DateFormat format = timeScale.createDateFormat();
            String[] labels = new String[len];
            Date dt = new Date(System.currentTimeMillis() - timeScale.tick * len);
            for (int k = 0; k < len; k++) {
                dt = new Date(dt.getTime() + timeScale.tick);
                labels[k] = format.format(dt);
            }

            // X-axis labels, thinned to roughly twenty. Because they are drawn vertically (rotated
            // 90°), adjacent labels no longer overlap horizontally the way wide horizontal text did,
            // so the chart can afford more ticks than the ~10 that fit when labels were horizontal.
            int labelStep = Math.max(1, (int) Math.ceil(len / 20.0));

            // Determine the y-axis range. Pick a "nice" step (1, 2 or 5 times a power of ten) so
            // the gridlines land on round numbers, then round the upper bound up to a multiple of
            // that step. Aiming for roughly ten gridlines gives finer resolution than a fixed
            // division by five, and keeps large magnitudes (e.g. memory usage in KB) on readable
            // increments such as 100k or 500k instead of awkward values like 209k.
            double maxValue = 0;
            for (float[] data : dataPoints)
                for (float v : data)
                    if (v > maxValue) maxValue = v;
            long yStep = niceStep(maxValue, 10);
            long yMax = Math.max(yStep, (long) Math.ceil(maxValue / yStep) * yStep);

            // Size the left margin to fit the widest y-axis label, so large values (e.g. memory
            // usage in KB) are not clipped.
            int widestLabel = 0;
            for (long v = 0; v <= yMax; v += yStep)
                widestLabel = Math.max(widestLabel, formatYLabel(v).length());
            double plotLeft = Math.max(48, widestLabel * 6.5 + 10);
            double plotW = plotRight - plotLeft;

            StringBuilder buf = new StringBuilder();
            buf.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
               .append(width).append(' ').append(height)
               .append("\" preserveAspectRatio=\"xMidYMid meet\" role=\"img\" class=\"jenkins-load-graph__svg\">");

            // Range gridlines + y-axis tick labels.
            for (long v = 0; v <= yMax; v += yStep) {
                double y = plotBottom - plotH * v / yMax;
                buf.append("<line x1=\"").append(fmt(plotLeft)).append("\" y1=\"").append(fmt(y))
                   .append("\" x2=\"").append(fmt(plotRight)).append("\" y2=\"").append(fmt(y))
                   .append("\" stroke=\"var(--text-color-secondary)\" stroke-opacity=\"0.25\" stroke-width=\"1\"/>");
                buf.append("<text x=\"").append(fmt(plotLeft - 6)).append("\" y=\"").append(fmt(y + 4))
                   .append("\" text-anchor=\"end\" class=\"jenkins-load-graph__label\">")
                   .append(formatYLabel(v)).append("</text>");
            }

            // Draw the x-axis labels rotated 90° (reading bottom-to-top) so long timestamps hang
            // vertically below the axis instead of overflowing the right edge of the chart.
            for (int k = 0; k < len; k += labelStep) {
                double x = len <= 1 ? plotLeft : plotLeft + plotW * k / (len - 1);
                double y = plotBottom + 8;
                buf.append("<text x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y))
                   .append("\" text-anchor=\"end\" dominant-baseline=\"central\" transform=\"rotate(-90 ")
                   .append(fmt(x)).append(' ').append(fmt(y))
                   .append(")\" class=\"jenkins-load-graph__label\">")
                   .append(Util.xmlEscape(labels[k])).append("</text>");
            }

            // Axis lines.
            buf.append("<line x1=\"").append(fmt(plotLeft)).append("\" y1=\"").append(fmt(plotTop))
               .append("\" x2=\"").append(fmt(plotLeft)).append("\" y2=\"").append(fmt(plotBottom))
               .append("\" stroke=\"var(--text-color-secondary)\" stroke-width=\"1\"/>");
            buf.append("<line x1=\"").append(fmt(plotLeft)).append("\" y1=\"").append(fmt(plotBottom))
               .append("\" x2=\"").append(fmt(plotRight)).append("\" y2=\"").append(fmt(plotBottom))
               .append("\" stroke=\"var(--text-color-secondary)\" stroke-width=\"1\"/>");

            // One polyline per series, drawn oldest -> newest (history is newest-first).
            for (int j = 0; j < series.size(); j++) {
                float[] data = dataPoints[j];
                StringBuilder points = new StringBuilder();
                for (int k = 0; k < len; k++) {
                    double x = len <= 1 ? plotLeft : plotLeft + plotW * k / (len - 1);
                    double y = plotBottom - plotH * data[len - 1 - k] / yMax;
                    if (k > 0) points.append(' ');
                    points.append(fmt(x)).append(',').append(fmt(y));
                }
                buf.append("<polyline fill=\"none\" stroke=\"").append(cssColor(series.get(j).color))
                   .append("\" stroke-width=\"1\" stroke-linejoin=\"round\" stroke-linecap=\"round\" points=\"")
                   .append(points).append("\"/>");
            }

            // Legend across the top.
            double lx = plotLeft;
            double ly = 16;
            for (MultiStageTimeSeries s : series) {
                String label = s.title.toString();
                buf.append("<line x1=\"").append(fmt(lx)).append("\" y1=\"").append(fmt(ly))
                   .append("\" x2=\"").append(fmt(lx + 18)).append("\" y2=\"").append(fmt(ly))
                   .append("\" stroke=\"").append(cssColor(s.color)).append("\" stroke-width=\"1\" stroke-linecap=\"round\"/>");
                buf.append("<text x=\"").append(fmt(lx + 24)).append("\" y=\"").append(fmt(ly + 4))
                   .append("\" class=\"jenkins-load-graph__label\">")
                   .append(Util.xmlEscape(label)).append("</text>");
                lx += 24 + label.length() * 7.0 + 24;
            }

            buf.append("</svg>");
            return buf.toString();
        }

        private static String fmt(double d) {
            return String.format(Locale.ROOT, "%.1f", d);
        }

        /**
         * Picks a "nice" axis step: the value of the form 1, 2 or 5 times a power of ten closest to
         * {@code range / targetTicks}, with a floor of 1 so the ticks stay whole numbers. This keeps
         * gridlines on round increments (e.g. 100k or 500k for memory usage) while aiming for roughly
         * {@code targetTicks} lines, giving finer resolution for small ranges (such as a handful of
         * executors) and readable steps for large ones.
         */
        private static long niceStep(double range, int targetTicks) {
            double raw = range / targetTicks;
            if (raw <= 1) return 1;
            double pow = Math.pow(10, Math.floor(Math.log10(raw)));
            long best = Math.max(1, Math.round(pow));
            double bestDist = Double.MAX_VALUE;
            // Candidate multipliers span the current decade and the start of the next (…, 1, 2, 5, 10).
            for (double mult : new double[] {1, 2, 5, 10}) {
                double candidate = mult * pow;
                double dist = Math.abs(candidate - raw);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = Math.max(1, Math.round(candidate));
                }
            }
            return best;
        }

        /**
         * Formats a y-axis tick value compactly so large magnitudes (e.g. memory usage in KB) stay
         * short: values below 1000 are shown verbatim, larger ones use a k/M/G suffix with at most
         * one decimal. Unit-agnostic — it compacts whatever unit the series already stores.
         */
        private static String formatYLabel(long v) {
            if (v < 1000) return Long.toString(v);
            String suffix;
            double scaled;
            if (v < 1_000_000) {
                scaled = v / 1000.0;
                suffix = "k";
            } else if (v < 1_000_000_000) {
                scaled = v / 1_000_000.0;
                suffix = "M";
            } else {
                scaled = v / 1_000_000_000.0;
                suffix = "G";
            }
            String s = String.format(Locale.ROOT, "%.1f", scaled);
            if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
            return s + suffix;
        }

        /**
         * Maps a series {@link Color} to a theme-aware CSS color. Known {@link ColorPalette}
         * constants are mapped to CSS custom properties so they adapt to the active theme;
         * anything else falls back to its literal hex value.
         */
        private static String cssColor(Color c) {
            if (ColorPalette.BLUE.equals(c)) return "var(--green)";
            if (ColorPalette.RED.equals(c)) return "var(--red)";
            if (ColorPalette.GREY.equals(c)) return "var(--text-color-secondary)";
            if (ColorPalette.YELLOW.equals(c)) return "var(--orange)";
            return String.format(Locale.ROOT, "#%06x", c.getRGB() & 0xFFFFFF);
        }

        /**
         * Renders this object as an image.
         */
        @Override
        public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
            ChartUtil.generateGraph(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp), createChart(), 500, 400);
        }
    }

    public static TrendChart createTrendChart(TimeScale scale, MultiStageTimeSeries... data) {
        return new TrendChart(scale, data);
    }

    private static final long serialVersionUID = 1L;
}
