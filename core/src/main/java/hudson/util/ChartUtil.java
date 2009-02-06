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

import hudson.model.AbstractBuild;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.data.category.CategoryDataset;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import java.awt.Font;
import java.awt.HeadlessException;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Chart generation utility code around JFreeChart.
 *
 * @see StackedAreaRenderer2
 * @see DataSetBuilder
 * @see ShiftedCategoryAxis
 * @author Kohsuke Kawaguchi
 */
public class ChartUtil {
    /**
     * Can be used as a graph label. Only displays numbers.
     */
    public static final class NumberOnlyBuildLabel implements Comparable<NumberOnlyBuildLabel> {
        public final AbstractBuild build;

        public NumberOnlyBuildLabel(AbstractBuild build) {
            this.build = build;
        }

        public int compareTo(NumberOnlyBuildLabel that) {
            return this.build.number-that.build.number;
        }

        public boolean equals(Object o) {
            if(!(o instanceof NumberOnlyBuildLabel))    return false;
            NumberOnlyBuildLabel that = (NumberOnlyBuildLabel) o;
            return build==that.build;
        }

        public int hashCode() {
            return build.hashCode();
        }

        public String toString() {
            return build.getDisplayName();
        }
    }

    /**
     * @deprecated
     *      Use {@code awtProblemCause!=null} instead. As of 1.267.
     */
    public static boolean awtProblem = false;

    /**
     * See issue 93. Detect an error in X11 and handle it gracefully.
     */
    public static Throwable awtProblemCause = null;

    /**
     * Generates the graph in PNG format and sends that to the response.
     *
     * @param defaultSize
     *      The size of the picture to be generated. These values can be overridden
     *      by the query paramter 'width' and 'height' in the request.
     */
    public static void generateGraph(StaplerRequest req, StaplerResponse rsp, JFreeChart chart, Area defaultSize) throws IOException {
        generateGraph(req,rsp,chart,defaultSize.width, defaultSize.height);
    }

    /**
     * Generates the graph in PNG format and sends that to the response.
     *
     * @param defaultW
     * @param defaultH
     *      The size of the picture to be generated. These values can be overridden
     *      by the query paramter 'width' and 'height' in the request.
     */
    public static void generateGraph(StaplerRequest req, StaplerResponse rsp, JFreeChart chart, int defaultW, int defaultH) throws IOException {
        try {
            String w = req.getParameter("width");
            if(w==null)     w=String.valueOf(defaultW);
            String h = req.getParameter("height");
            if(h==null)     h=String.valueOf(defaultH);
            BufferedImage image = chart.createBufferedImage(Integer.parseInt(w),Integer.parseInt(h));
            rsp.setContentType("image/png");
            ServletOutputStream os = rsp.getOutputStream();
            ImageIO.write(image, "PNG", os);
            os.close();
        } catch(HeadlessException e) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath()+"/images/headless.png");
        }
    }

    /**
     * Generates the clickable map info and sends that to the response.
     */
    public static void generateClickableMap(StaplerRequest req, StaplerResponse rsp, JFreeChart chart, Area defaultSize) throws IOException {
        generateClickableMap(req,rsp,chart,defaultSize.width,defaultSize.height);
    }

    /**
     * Generates the clickable map info and sends that to the response.
     */
    public static void generateClickableMap(StaplerRequest req, StaplerResponse rsp, JFreeChart chart, int defaultW, int defaultH) throws IOException {
        String w = req.getParameter("width");
        if(w==null)     w=String.valueOf(defaultW);
        String h = req.getParameter("height");
        if(h==null)     h=String.valueOf(defaultH);

        ChartRenderingInfo info = new ChartRenderingInfo();
        chart.createBufferedImage(Integer.parseInt(w),Integer.parseInt(h),info);

        rsp.setContentType("text/plain;charset=UTF-8");
        rsp.getWriter().println(ChartUtilities.getImageMap( "map", info ));
    }

    /**
     * Adjusts the Y-axis so that abnormally large value won't spoil the whole chart
     * by making everything look virtually 0.
     *
     * <p>
     * The algorithm is based on <a href="http://en.wikipedia.org/wiki/Chebyshev%27s_inequality">Chebyshev's inequality</a>,
     * which states that given any number sequence, nore more than 1/(N^2) values are more than N x stddev away
     * from the average.
     *
     * <p>
     * So the algorithm is to set Y-axis range so that we can see all data points that are within N x stddev
     * of the average. Most of the time, Cebyshev's inequality is very conservative, so it shouldn't do
     * much harm.
     *
     * <p>
     * When the algorithm does kick in, however, we can kick out at most 1 in N^2 data points.
     * (So for example if N=3 then we can "fix" the graph as long as we only have less than 1/(3*3)=11.111...% bad data.
     *
     * <p>
     * Also see issue #1246.
     */
    public static void adjustChebyshev(CategoryDataset dataset, NumberAxis yAxis) {
        // first compute E(X) and Var(X)
        double sum=0,sum2=0;

        final int nColumns = dataset.getColumnCount();
        final int nRows    = dataset.getRowCount();
        for (int i=0; i<nRows; i++ ) {
            Comparable rowKey = dataset.getRowKey(i);
            for( int j=0; j<nColumns; j++) {
                Comparable columnKey = dataset.getColumnKey(j);

                double n = dataset.getValue(rowKey,columnKey).doubleValue();
                sum += n;
                sum2 +=n*n;
            }
        }

        double average = sum/(nColumns*nRows);
        double stddev = Math.sqrt(sum2/(nColumns*nRows)-average*average);

        double rangeMin = average-stddev*CHEBYSHEV_N;
        double rangeMax = average+stddev*CHEBYSHEV_N;

        // now see if there are any data points that fall outside (rangeMin,rangeMax)
        boolean found = false;
        double min=0,max=0;
        for (int i=0; i<nRows; i++ ) {
            Comparable rowKey = dataset.getRowKey(i);
            for( int j=0; j<nColumns; j++) {
                Comparable columnKey = dataset.getColumnKey(j);

                double n = dataset.getValue(rowKey,columnKey).doubleValue();
                if(n<rangeMin || rangeMax<n) {
                    found = true;
                    continue;   // ignore this value
                }

                min = Math.min(min,n);
                max = Math.max(max,n);
            }
        }

        if(!found)
            return; // no adjustment was necessary

        // some values fell outside the range, so adjust the Y-axis

        // if we are ever to extend this method to handle negative value ranges correctly,
        // the code after this needs modifications

        min = Math.min(0,min);  // always include 0 in the graph
        max += yAxis.getUpperMargin()*(max-min);

        yAxis.setRange(min,max);
    }

    public static double CHEBYSHEV_N = 3;

    static {
        try {
            new Font("SansSerif",Font.BOLD,18).toString();
        } catch (Throwable t) {
            awtProblemCause = t;
            awtProblem = true;
        }
    }
}
