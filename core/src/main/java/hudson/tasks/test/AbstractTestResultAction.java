package hudson.tasks.test;

import hudson.Functions;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.Project;
import hudson.model.Result;
import hudson.util.ChartUtil;
import hudson.util.ChartUtil.NumberOnlyBuildLabel;
import hudson.util.ColorPalette;
import hudson.util.DataSetBuilder;
import hudson.util.ShiftedCategoryAxis;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.AreaRendererEndType;
import org.jfree.chart.renderer.category.AreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.awt.Color;
import java.io.IOException;

/**
 * Common base class for recording test result.
 *
 * <p>
 * {@link Project} and {@link Build} recognizes {@link Action}s that derive from this,
 * and displays it nicely (regardless of the underlying implementation.)
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractTestResultAction<T extends AbstractTestResultAction> implements Action {
    public final Build owner;

    protected AbstractTestResultAction(Build owner) {
        this.owner = owner;
    }

    /**
     * Gets the number of failed tests.
     */
    public abstract int getFailCount();

    /**
     * Gets the total number of tests.
     */
    public abstract int getTotalCount();

    /**
     * Gets the diff string of failures.
     */
    public final String getFailureDiffString() {
        T prev = getPreviousResult();
        if(prev==null)  return "";  // no record

        return " / "+Functions.getDiffString(this.getFailCount()-prev.getFailCount());
    }

    public String getDisplayName() {
        return "Test Result";
    }

    public String getUrlName() {
        return "testReport";
    }

    public String getIconFileName() {
        return "clipboard.gif";
    }

    /**
     * Gets the test result of the previous build, if it's recorded, or null.
     */
    public T getPreviousResult() {
        return (T)getPreviousResult(getClass());
    }

    private <U extends AbstractTestResultAction> U getPreviousResult(Class<U> type) {
        Build b = owner;
        while(true) {
            b = b.getPreviousBuild();
            if(b==null)
                return null;
            if(b.getResult()== Result.FAILURE)
                continue;
            U r = b.getAction(type);
            if(r!=null)
                return r;
        }
    }

    /**
     * Generates a PNG image for the test result trend.
     */
    public void doGraph( StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(ChartUtil.awtProblem) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath()+"/images/headless.png");
            return;
        }

        if(req.checkIfModified(owner.getTimestamp(),rsp))
            return;

        boolean failureOnly = Boolean.valueOf(req.getParameter("failureOnly"));

        DataSetBuilder<String,NumberOnlyBuildLabel> dsb = new DataSetBuilder<String,NumberOnlyBuildLabel>();

        for( AbstractTestResultAction<?> a=this; a!=null; a=a.getPreviousResult(AbstractTestResultAction.class) ) {
            dsb.add( a.getFailCount(), "failed", new NumberOnlyBuildLabel(a.owner));
            if(!failureOnly)
                dsb.add( a.getTotalCount()-a.getFailCount(),"total", new NumberOnlyBuildLabel(a.owner));
        }

        ChartUtil.generateGraph(req,rsp,createChart(dsb.build()),500,200);
    }

    private JFreeChart createChart(CategoryDataset dataset) {

        final JFreeChart chart = ChartFactory.createStackedAreaChart(
            null,                   // chart title
            null,                   // unused
            "count",                  // range axis label
            dataset,                  // data
            PlotOrientation.VERTICAL, // orientation
            false,                     // include legend
            true,                     // tooltips
            false                     // urls
        );

        // NOW DO SOME OPTIONAL CUSTOMISATION OF THE CHART...

        // set the background color for the chart...

//        final StandardLegend legend = (StandardLegend) chart.getLegend();
//        legend.setAnchor(StandardLegend.SOUTH);

        chart.setBackgroundPaint(Color.white);

        final CategoryPlot plot = chart.getCategoryPlot();

        // plot.setAxisOffset(new Spacer(Spacer.ABSOLUTE, 5.0, 5.0, 5.0, 5.0));
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(null);
        plot.setForegroundAlpha(0.8f);
//        plot.setDomainGridlinesVisible(true);
//        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.black);

        CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
        plot.setDomainAxis(domainAxis);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.0);
        domainAxis.setCategoryMargin(0.0);

        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        AreaRenderer ar = (AreaRenderer) plot.getRenderer();
        ar.setEndType(AreaRendererEndType.TRUNCATE);
        ar.setSeriesPaint(0,ColorPalette.RED);
        ar.setSeriesPaint(1,ColorPalette.BLUE);

        // crop extra space around the graph
        plot.setInsets(new RectangleInsets(0,0,0,5.0));

        return chart;
    }
}
