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

import hudson.util.TimeUnit2;
import hudson.util.NoOverlapCategoryAxis;
import hudson.util.ChartUtil;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.awt.*;
import java.util.Locale;

import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.ui.RectangleInsets;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;

/**
 * Maintains several {@link TimeSeries} with different update frequencies to satisfy three goals;
 * (1) retain data over long timespan, (2) save memory, and (3) retain accurate data for the recent past.
 *
 * All in all, one instance uses about 8KB space.
 *
 * @author Kohsuke Kawaguchi
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
     * Updated every 10 seconds. Keep data up to 1 hour.
     */
    @Exported
    public final TimeSeries sec10;
    /**
     * Updated every 1 min. Keep data up to 1 day.
     */
    @Exported
    public final TimeSeries min;
    /**
     * Updated every 1 hour. Keep data up to 4 weeks.
     */
    @Exported
    public final TimeSeries hour;

    private int counter;

    private static final Font CHART_FONT = Font.getFont(MultiStageTimeSeries.class.getName() + ".chartFont",
            new Font(Font.SANS_SERIF, Font.PLAIN, 10));

    public MultiStageTimeSeries(Localizable title, Color color, float initialValue, float decay) {
        this.title = title;
        this.color = color;
        this.sec10 = new TimeSeries(initialValue,decay,6*60);
        this.min = new TimeSeries(initialValue,decay,60*24);
        this.hour = new TimeSeries(initialValue,decay,28*24);
    }

    /**
     * @deprecated since 2009-04-05.
     *      Use {@link #MultiStageTimeSeries(Localizable, Color, float, float)}
     */
    @Deprecated
    public MultiStageTimeSeries(float initialValue, float decay) {
        this(Messages._MultiStageTimeSeries_EMPTY_STRING(), Color.WHITE, initialValue,decay);
    }

    /**
     * Call this method every 10 sec and supply a new data point.
     */
    public void update(float f) {
        counter = (counter+1)%360;   // 1hour/10sec = 60mins/10sec=3600secs/10sec = 360
        sec10.update(f);
        if(counter%6==0)    min.update(f);
        if(counter==0)      hour.update(f);
    }

    /**
     * Selects a {@link TimeSeries}.
     */
    public TimeSeries pick(TimeScale timeScale) {
        switch (timeScale) {
        case HOUR:  return hour;
        case MIN:   return min;
        case SEC10: return sec10;
        default:    throw new AssertionError();
        }
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
        SEC10(TimeUnit2.SECONDS.toMillis(10)),
        MIN(TimeUnit2.MINUTES.toMillis(1)),
        HOUR(TimeUnit2.HOURS.toMillis(1));

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
            switch (this) {
            case HOUR:  return new SimpleDateFormat("MMM/dd HH");
            case MIN:   return new SimpleDateFormat("HH:mm");
            case SEC10: return new SimpleDateFormat("HH:mm:ss");
            default:    throw new AssertionError();
            }
        }

        /**
         * Parses the {@link TimeScale} from the query parameter.
         */
        public static TimeScale parse(String type) {
            if(type==null)   return TimeScale.MIN;
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
            this.series = new ArrayList<MultiStageTimeSeries>(Arrays.asList(series));
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
                assert dataLength ==dataPoint.length;

            DefaultCategoryDataset ds = new DefaultCategoryDataset();

            DateFormat format = timeScale.createDateFormat();

            Date dt = new Date(System.currentTimeMillis()-timeScale.tick*dataLength);
            for (int i = dataLength-1; i>=0; i--) {
                dt = new Date(dt.getTime()+timeScale.tick);
                String l = format.format(dt);
                for(int j=0; j<dataPoints.length; j++)
                    ds.addValue(dataPoints[j][i],series.get(j).title.toString(),l);
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
         * Renders this object as an image.
         */
        public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
            ChartUtil.generateGraph(req, rsp, createChart(), 500, 400);
        }
    }

    public static TrendChart createTrendChart(TimeScale scale, MultiStageTimeSeries... data) {
        return new TrendChart(scale,data);
    }

    private static final long serialVersionUID = 1L;
}
