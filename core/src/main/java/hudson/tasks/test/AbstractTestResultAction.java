/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Red Hat, Inc., Stephen Connolly, id:cactusman, Yahoo!, Inc.
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
package hudson.tasks.test;

import hudson.Functions;
import hudson.model.*;
import hudson.util.*;
import hudson.util.ChartUtil.NumberOnlyBuildLabel;

import java.awt.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jenkins.model.RunAction2;
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
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Common base class for recording test result.
 *
 * <p>
 * {@link Project} and {@link Build} recognizes {@link Action}s that derive from this,
 * and displays it nicely (regardless of the underlying implementation.)
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class AbstractTestResultAction<T extends AbstractTestResultAction> implements HealthReportingAction, RunAction2 {
    public transient AbstractBuild<?,?> owner;

    private Map<String,String> descriptions = new ConcurrentHashMap<String, String>();

    /** @since 1.545 */
    protected AbstractTestResultAction() {}

    /** @deprecated Use the default constructor and just call {@link Run#addAction} to associate the build with the action. */
    @Deprecated
    protected AbstractTestResultAction(AbstractBuild owner) {
        this.owner = owner;
    }

    @Override public void onAttached(Run<?, ?> r) {
        this.owner = (AbstractBuild<?,?>) r;
    }

    @Override public void onLoad(Run<?, ?> r) {
        this.owner = (AbstractBuild<?,?>) r;
    }

    /**
     * Gets the number of failed tests.
     */
    @Exported(visibility=2)
    public abstract int getFailCount();

    /**
     * Gets the number of skipped tests.
     */
    @Exported(visibility=2)
    public int getSkipCount() {
        // Not all sub-classes will understand the concept of skipped tests.
        // This default implementation is for them, so that they don't have
        // to implement it (this avoids breaking existing plug-ins - i.e. those
        // written before this method was added in 1.178).
        // Sub-classes that do support skipped tests should over-ride this method.
        return 0;
    }

    /**
     * Gets the total number of tests.
     */
    @Exported(visibility=2)
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
        return Messages.AbstractTestResultAction_getDisplayName();
    }

    @Exported(visibility=2)
    public String getUrlName() {
        return "testReport";
    }

    public String getIconFileName() {
        return "clipboard.png";
    }

    public HealthReport getBuildHealth() {
        final int totalCount = getTotalCount();
        final int failCount = getFailCount();
        int score = (totalCount == 0) ? 100 : (int) (100.0 * (1.0 - ((double)failCount) / totalCount));
        Localizable description, displayName = Messages._AbstractTestResultAction_getDisplayName();
        if (totalCount == 0) {
        	description = Messages._AbstractTestResultAction_zeroTestDescription(displayName);
        } else {
        	description = Messages._AbstractTestResultAction_TestsDescription(displayName, failCount, totalCount);
        }
        return new HealthReport(score, description);
    }

    /**
     * Exposes this object to the remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Returns the object that represents the actual test result.
     * This method is used by the remote API so that the XML/JSON
     * that we are sending won't contain unnecessary indirection
     * (that is, {@link AbstractTestResultAction} in between.
     *
     * <p>
     * If such a concept doesn't make sense for a particular subtype,
     * return <tt>this</tt>.
     */
    public abstract Object getResult();

    /**
     * Gets the test result of the previous build, if it's recorded, or null.
     */
    public T getPreviousResult() {
        return (T)getPreviousResult(getClass(), true);
    }

    private <U extends AbstractTestResultAction> U getPreviousResult(Class<U> type, boolean eager) {
        Set<Integer> loadedBuilds = eager ? null : owner.getProject()._getRuns().getLoadedBuilds().keySet();
        AbstractBuild<?,?> b = owner;
        while(true) {
            b = eager || loadedBuilds.contains(b.number - /* assuming there are no gaps */1) ? b.getPreviousBuild() : null;
            if(b==null)
                return null;
            U r = b.getAction(type);
            if(r!=null)
                return r;
        }
    }
    
    public TestResult findPreviousCorresponding(TestResult test) {
        T previousResult = getPreviousResult();
        if (previousResult != null) {
            TestResult testResult = (TestResult)getResult();
            return testResult.findCorrespondingResult(test.getId());
        }

        return null;
    }

    public TestResult findCorrespondingResult(String id) {
        return ((TestResult)getResult()).findCorrespondingResult(id);
    }
    
    /**
     * A shortcut for summary.jelly
     * 
     * @return List of failed tests from associated test result.
     */
    public List<? extends TestResult> getFailedTests() {
        return Collections.emptyList();
    }

    /**
     * Generates a PNG image for the test result trend.
     */
    public void doGraph( StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(ChartUtil.awtProblemCause!=null) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath()+"/images/headless.png");
            return;
        }

        if(req.checkIfModified(owner.getTimestamp(),rsp))
            return;

        ChartUtil.generateGraph(req,rsp,createChart(req,buildDataSet(req)),calcDefaultSize());
    }

    /**
     * Generates a clickable map HTML for {@link #doGraph(StaplerRequest, StaplerResponse)}.
     */
    public void doGraphMap( StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(req.checkIfModified(owner.getTimestamp(),rsp))
            return;
        ChartUtil.generateClickableMap(req,rsp,createChart(req,buildDataSet(req)),calcDefaultSize());
    }

    /**
     * Returns a full path down to a test result
     */
    public String getTestResultPath(TestResult it) {
        return getUrlName() + "/" + it.getRelativePathFrom(null);
    }

    /**
     * Determines the default size of the trend graph.
     *
     * This is default because the query parameter can choose arbitrary size.
     * If the screen resolution is too low, use a smaller size.
     */
    private Area calcDefaultSize() {
        Area res = Functions.getScreenResolution();
        if(res!=null && res.width<=800)
            return new Area(250,100);
        else
            return new Area(500,200);
    }
    
    private CategoryDataset buildDataSet(StaplerRequest req) {
        boolean failureOnly = Boolean.valueOf(req.getParameter("failureOnly"));

        DataSetBuilder<String,NumberOnlyBuildLabel> dsb = new DataSetBuilder<String,NumberOnlyBuildLabel>();

        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            dsb.add( a.getFailCount(), "failed", new NumberOnlyBuildLabel(a.owner));
            if(!failureOnly) {
                dsb.add( a.getSkipCount(), "skipped", new NumberOnlyBuildLabel(a.owner));
                dsb.add( a.getTotalCount()-a.getFailCount()-a.getSkipCount(),"total", new NumberOnlyBuildLabel(a.owner));
            }
        }
        return dsb.build();
    }

    private JFreeChart createChart(StaplerRequest req,CategoryDataset dataset) {

        final String relPath = getRelPath(req);

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

        StackedAreaRenderer ar = new StackedAreaRenderer2() {
            @Override
            public String generateURL(CategoryDataset dataset, int row, int column) {
                NumberOnlyBuildLabel label = (NumberOnlyBuildLabel) dataset.getColumnKey(column);
                return relPath+label.build.getNumber()+"/testReport/";
            }

            @Override
            public String generateToolTip(CategoryDataset dataset, int row, int column) {
                NumberOnlyBuildLabel label = (NumberOnlyBuildLabel) dataset.getColumnKey(column);
                AbstractTestResultAction a = label.build.getAction(AbstractTestResultAction.class);
                switch (row) {
                    case 0:
                        return String.valueOf(Messages.AbstractTestResultAction_fail(label.build.getDisplayName(), a.getFailCount()));
                    case 1:
                        return String.valueOf(Messages.AbstractTestResultAction_skip(label.build.getDisplayName(), a.getSkipCount()));
                    default:
                        return String.valueOf(Messages.AbstractTestResultAction_test(label.build.getDisplayName(), a.getTotalCount()));
                }
            }
        };
        plot.setRenderer(ar);
        ar.setSeriesPaint(0,ColorPalette.RED); // Failures.
        ar.setSeriesPaint(1,ColorPalette.YELLOW); // Skips.
        ar.setSeriesPaint(2,ColorPalette.BLUE); // Total.

        // crop extra space around the graph
        plot.setInsets(new RectangleInsets(0,0,0,5.0));

        return chart;
    }

    private String getRelPath(StaplerRequest req) {
        String relPath = req.getParameter("rel");
        if(relPath==null)   return "";
        return relPath;
    }

    /**
     * {@link TestObject}s do not have their own persistence mechanism, so updatable data of {@link TestObject}s
     * need to be persisted by the owning {@link AbstractTestResultAction}, and this method and
     * {@link #setDescription(TestObject, String)} provides that logic.
     *
     * <p>
     * The default implementation stores information in the 'this' object.
     *
     * @see TestObject#getDescription() 
     */
    protected String getDescription(TestObject object) {
    	return descriptions.get(object.getId());
    }

    protected void setDescription(TestObject object, String description) {
    	descriptions.put(object.getId(), description);
    }

    public Object readResolve() {
    	if (descriptions == null) {
    		descriptions = new ConcurrentHashMap<String, String>();
    	}
    	
    	return this;
    }
}
