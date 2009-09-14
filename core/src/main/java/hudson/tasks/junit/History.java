/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Tom Huybrechts
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
package hudson.tasks.junit;

import hudson.model.AbstractBuild;
import hudson.util.ChartUtil;
import hudson.util.ColorPalette;
import hudson.util.DataSetBuilder;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;
import hudson.util.Graph;

import java.awt.Color;
import java.awt.Paint;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;

/**
 * History of {@link TestObject} over time.
 *
 * @since 1.320
 */
public class History {
	private final TestObject testObject;

	public History(TestObject testObject) {
		this.testObject = testObject;
	}

	public TestObject getTestObject() {
		return testObject;
	}
	
	public List<TestObject> getList() {
		List<TestObject> list = new ArrayList<TestObject>();
		for (AbstractBuild<?,?> b: testObject.getOwner().getParent().getBuilds()) {
			if (b.isBuilding()) continue;
			TestObject o = testObject.getResultInBuild(b);
			if (o != null) {
				list.add(o);
			}
		}
		return list;
	}

    /**
     * Graph of duration of tests over time.
     */
    public Graph getDurationGraph() {
       return new GraphImpl("seconds") {
           protected DataSetBuilder<String, ChartLabel> createDataSet() {
               DataSetBuilder<String, ChartLabel> data = new DataSetBuilder<String, ChartLabel>();
               for (TestObject o: getList()) {
                   data.add(((double) o.getDuration()) / (1000), "", new ChartLabel(o)  {
                       @Override
                       public Color getColor() {
                           if (o.getFailCount() > 0)
                               return ColorPalette.RED;
                           else if (o.getSkipCount() > 0)
                               return ColorPalette.YELLOW;
                           else
                               return ColorPalette.BLUE;
                       }
                   });
               }
               return data;
           }
       };
    }

    /**
     * Graph of # of tests over time.
     */
    public Graph getCountGraph() {
        return new GraphImpl("") {
            protected DataSetBuilder<String, ChartLabel> createDataSet() {
                DataSetBuilder<String, ChartLabel> data = new DataSetBuilder<String, ChartLabel>();

                for (TestObject o: getList()) {
                    data.add(o.getPassCount(), "2Passed", new ChartLabel(o));
                    data.add(o.getFailCount(), "1Failed", new ChartLabel(o));
                    data.add(o.getSkipCount(), "0Skipped", new ChartLabel(o));
                }
                return data;
            }
        };
    }

    private abstract class GraphImpl extends Graph {
        private final String yLabel;

        protected GraphImpl(String yLabel) {
            super(testObject.getOwner().getTimestamp(),500,400);
            this.yLabel =  yLabel;
        }

        protected abstract DataSetBuilder<String, ChartLabel> createDataSet();

        protected JFreeChart createGraph() {
            final CategoryDataset dataset = createDataSet().build();

            final JFreeChart chart = ChartFactory.createStackedAreaChart(null, // chart
                                                                                // title
                    null, // unused
                    yLabel, // range axis label
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
            // plot.setDomainGridlinesVisible(true);
            // plot.setDomainGridlinePaint(Color.white);
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
            rangeAxis.setAutoRange(true);

            StackedAreaRenderer ar = new StackedAreaRenderer2() {
                @Override
                public Paint getItemPaint(int row, int column) {
                    ChartLabel key = (ChartLabel) dataset.getColumnKey(column);
                    if (key.getColor() != null) return key.getColor();
                    return super.getItemPaint(row, column);
                }

                @Override
                public String generateURL(CategoryDataset dataset, int row,
                        int column) {
                    ChartLabel label = (ChartLabel) dataset.getColumnKey(column);
                    return String.valueOf(label.o.getOwner().number);
                }

                @Override
                public String generateToolTip(CategoryDataset dataset, int row,
                        int column) {
                    ChartLabel label = (ChartLabel) dataset.getColumnKey(column);
                    return label.o.getOwner().getDisplayName() + " : "
                            + label.o.getDurationString();
                }
            };
            plot.setRenderer(ar);
            ar.setSeriesPaint(0,ColorPalette.RED); // Failures.
            ar.setSeriesPaint(1,ColorPalette.YELLOW); // Skips.
            ar.setSeriesPaint(2,ColorPalette.BLUE); // Total.

            // crop extra space around the graph
            plot.setInsets(new RectangleInsets(0, 0, 0, 5.0));

            return chart;
        }
    }

    class ChartLabel implements Comparable<ChartLabel> {
    	TestObject o;
        public ChartLabel(TestObject o) {
            this.o = o;
        }

        public int compareTo(ChartLabel that) {
            return this.o.getOwner().number - that.o.getOwner().number;
        }

        @Override
        public boolean equals(Object o) {
        	if (!(o instanceof ChartLabel)) {
            	return false;
            }
            ChartLabel that = (ChartLabel) o;
            return this.o == that.o;
        }

        public Color getColor() {
        	return null;
        }

        @Override
        public int hashCode() {
            return o.hashCode();
        }

        @Override
        public String toString() {
            String l = o.getOwner().getDisplayName();
            String s = o.getOwner().getBuiltOnStr();
            if (s != null)
                l += ' ' + s;
            return l;
//            return o.getDisplayName() + " " + o.getOwner().getDisplayName();
        }

    }

}
