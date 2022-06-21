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

import hudson.util.Graph;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.awt.Color;
import java.awt.Paint;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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

    public final Result result;

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

    public MultiStageTimeSeries(Localizable title, Result result, float initialValue, float decay) {
        this.title = title;
        this.result = result;
        this.color = Graph.getColor(result);
        this.sec10 = new TimeSeries(initialValue, decay, 6 * (int) TimeUnit.HOURS.toMinutes(6));
        this.min = new TimeSeries(initialValue, decay, (int) TimeUnit.DAYS.toMinutes(2));
        this.hour = new TimeSeries(initialValue, decay, (int) TimeUnit.DAYS.toHours(56));
    }

    /**
     * @deprecated since 2009-04-05.
     *      Use {@link #MultiStageTimeSeries(Localizable, Result, float, float)}
     */
    @Deprecated
    public MultiStageTimeSeries(float initialValue, float decay) {
        this(Messages._MultiStageTimeSeries_EMPTY_STRING(), Result.NOT_BUILT, initialValue, decay);
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
            switch (this) {
            case HOUR:  return new SimpleDateFormat("MMM/dd HH");
            case MIN:   return new SimpleDateFormat("E HH:mm");
            case SEC10: return new SimpleDateFormat("HH:mm:ss");
            default:    throw new AssertionError();
            }
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
    public static class TrendChart implements Graph.GraphSource {
        public final TimeScale timeScale;
        public final List<MultiStageTimeSeries> series;
        private List<String> columnKeys;

        public TrendChart(TimeScale timeScale, MultiStageTimeSeries... series) {
            this.timeScale = timeScale;
            this.series = new ArrayList<>(Arrays.asList(series));
        }

        /**
         * Renders this object as an image.
         */
        public Graph getGraph() {
            return new Graph(-1, 500, 400, this);
        }

        @Override
        public String getRowKey(int j) {
            return series.get(j).title.toString();
        }

        @Override
        public String getColumnKey(int idx) {
            if (columnKeys == null) {
                columnKeys = new ArrayList<>();
                DateFormat format = timeScale.createDateFormat();
                int dataLength = series.get(0).pick(timeScale).getHistory().length;
                Date dt = new Date(System.currentTimeMillis() - timeScale.tick * dataLength);
                for (int i = dataLength - 1; i >= 0; i--) {
                    dt = new Date(dt.getTime() + timeScale.tick);
                    String l = format.format(dt);
                    columnKeys.add(l);
                }
            }
            return columnKeys.get(idx);
        }

        @Override
        public float[] getDataPoints(int i) {
            return series.get(i).pick(timeScale).getHistory();
        }

        @Override
        public int getColumnCount() {
            return series.size();
        }

        @Override
        public Result getColor(int i) {
            return series.get(i).result;
        }

        @Override
        public String getTooltip(int column, int row) {
            return null;
        }

    }

    public static Graph createTrendChart(TimeScale scale, MultiStageTimeSeries... data) {
        return new TrendChart(scale, data).getGraph();
    }

    private static final long serialVersionUID = 1L;
}
