package hudson.tasks.junit;

import hudson.model.AbstractBuild;
import hudson.util.ChartUtil;
import hudson.util.ColorPalette;
import hudson.util.DataSetBuilder;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;

import java.awt.Color;
import java.awt.Paint;
import java.io.IOException;
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
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

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

	public void doDurationGraph(StaplerRequest req, StaplerResponse rsp) throws IOException {
		if (req.checkIfModified(testObject.getOwner().getTimestamp(), rsp)) return;
		ChartUtil.generateGraph(req, rsp, createGraph(getDurationData(), "seconds"), 500, 400);
	}

	public void doDurationMap(StaplerRequest req, StaplerResponse rsp) throws IOException {
		if (req.checkIfModified(testObject.getOwner().getTimestamp(), rsp)) return;
		ChartUtil.generateClickableMap(req, rsp, createGraph(getDurationData(), ""), 500, 400);
	}

	private DataSetBuilder<String, ChartLabel> getDurationData() {
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

	private DataSetBuilder<String, ChartLabel> getCountData() {
		DataSetBuilder<String, ChartLabel> data = new DataSetBuilder<String, ChartLabel>();
		
        for (TestObject o: getList()) {
        	data.add(o.getPassCount(), "2Passed", new ChartLabel(o));
        	data.add(o.getFailCount(), "1Failed", new ChartLabel(o));
        	data.add(o.getSkipCount(), "0Skipped", new ChartLabel(o));
        }
		return data;
	}

	public void doCountGraph(StaplerRequest req, StaplerResponse rsp) throws IOException {
		if (req.checkIfModified(testObject.getOwner().getTimestamp(), rsp)) return;
		ChartUtil.generateGraph(req, rsp, createGraph(getCountData(), ""), 500, 400);
	}

	public void doCountMap(StaplerRequest req, StaplerResponse rsp) throws IOException {
		if (req.checkIfModified(testObject.getOwner().getTimestamp(), rsp)) return;
		ChartUtil.generateClickableMap(req, rsp, createGraph(getCountData(), ""), 500, 400);
	}

	
	private JFreeChart createGraph(DataSetBuilder<String, ChartLabel> data, String yLabel) {
		final CategoryDataset dataset = data.build();

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

    class ChartLabel implements Comparable<ChartLabel> {
    	TestObject o;
        public ChartLabel(TestObject o) {
            this.o = o;
        }

        public int compareTo(ChartLabel that) {
        	int result = this.o.getOwner().number - that.o.getOwner().number;
            return result;
        }

        public boolean equals(Object o) {
        	if (!(o instanceof ChartLabel)) {
            	return false;
            }
            ChartLabel that = (ChartLabel) o;
            boolean result = this.o == that.o;
            return result;
        }

        public Color getColor() {
        	return null;
        }

        public int hashCode() {
            return o.hashCode();
        }

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
