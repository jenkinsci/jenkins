package hudson.model;

import com.thoughtworks.xstream.XStream;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.XmlFile;
import hudson.tasks.BuildTrigger;
import hudson.tasks.LogRotator;
import hudson.util.ChartUtil;
import hudson.util.ColorPalette;
import hudson.util.DataSetBuilder;
import hudson.util.IOException2;
import hudson.util.RunList;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;
import hudson.util.TextFile;
import hudson.util.XStream2;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
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

import javax.servlet.ServletException;
import java.awt.Color;
import java.awt.Paint;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;

/**
 * A job is an runnable entity under the monitoring of Hudson.
 *
 * <p>
 * Every time it "runs", it will be recorded as a {@link Run} object.
 *
 * <p>
 * To register a custom {@link Job} class from a plugin, add it to
 * {@link Jobs#JOBS}. Also see {@link Job#XSTREAM}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Job<JobT extends Job<JobT,RunT>, RunT extends Run<JobT,RunT>>
        extends DirectoryHolder implements Describable<Job<JobT,RunT>>, ExtensionPoint {
    /**
     * Project name.
     */
    protected /*final*/ transient String name;

    /**
     * Project description. Can be HTML.
     */
    protected String description;

    /**
     * Root directory for this job.
     */
    protected transient File root;

    /**
     * Next build number.
     * Kept in a separate file because this is the only information
     * that gets updated often. This allows the rest of the configuration
     * to be in the VCS.
     * <p>
     * In 1.28 and earlier, this field was stored in the project configuration file,
     * so even though this is marked as transient, don't move it around.
     */
    protected transient int nextBuildNumber = 1;
    private transient Hudson parent;

    private LogRotator logRotator;

    private boolean keepDependencies;

    protected Job(Hudson parent,String name) {
        this.parent = parent;
        doSetName(name);
        getBuildDir().mkdirs();
    }

    /**
     * Called when a {@link Job} is loaded from disk.
     */
    protected void onLoad(Hudson root, String name) throws IOException {
        this.parent = root;
        doSetName(name);

        TextFile f = getNextBuildNumberFile();
        if(f.exists()) {
            // starting 1.28, we store nextBuildNumber in a separate file.
            // but old Hudson didn't do it, so if the file doesn't exist,
            // assume that nextBuildNumber was read from config.xml
            try {
                this.nextBuildNumber = Integer.parseInt(f.readTrim());
            } catch (NumberFormatException e) {
                throw new IOException2(f+" doesn't contain a number",e);
            }
        } else {
            // this must be the old Hudson. create this file now.
            saveNextBuildNumber();
            save(); // and delete it from the config.xml
        }
    }

    /**
     * Just update {@link #name} and {@link #root}, since they are linked.
     */
    private void doSetName(String name) {
        this.name = name;
        this.root = new File(new File(parent.root,"jobs"),name);
    }

    public File getRootDir() {
        return root;
    }

    private TextFile getNextBuildNumberFile() {
        return new TextFile(new File(this.root,"nextBuildNumber"));
    }

    private void saveNextBuildNumber() throws IOException {
        getNextBuildNumberFile().write(String.valueOf(nextBuildNumber)+'\n');
    }

    public final Hudson getParent() {
        return parent;
    }

    public boolean isInQueue() {
        return false;
    }

    /**
     * If true, it will keep all the build logs of dependency components.
     */
    public boolean isKeepDependencies() {
        return keepDependencies;
    }

    /**
     * Allocates a new buildCommand number.
     */
    public synchronized int assignBuildNumber() throws IOException {
        int r = nextBuildNumber++;
        saveNextBuildNumber();
        return r;
    }

    public int getNextBuildNumber() {
        return nextBuildNumber;
    }

    /**
     * Gets the project description HTML.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the project description HTML.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the log rotator for this job, or null if none.
     */
    public LogRotator getLogRotator() {
        return logRotator;
    }

    public void setLogRotator(LogRotator logRotator) {
        this.logRotator = logRotator;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return getName();
    }

    /**
     * Renames a job.
     */
    public void renameTo(String newName) throws IOException {
        // always synchronize from bigger objects first
        synchronized(parent) {
            synchronized(this) {
                // sanity check
                if(newName==null)
                    throw new IllegalArgumentException("New name is not given");
                if(parent.getJob(newName)!=null)
                    throw new IllegalArgumentException("Job "+newName+" already exists");

                // noop?
                if(this.name.equals(newName))
                    return;


                String oldName = this.name;
                File oldRoot = this.root;

                doSetName(newName);
                File newRoot = this.root;

                {// rename data files
                    boolean interrupted=false;
                    boolean renamed = false;

                    // try to rename the job directory.
                    // this may fail on Windows due to some other processes accessing a file.
                    // so retry few times before we fall back to copy.
                    for( int retry=0; retry<5; retry++ ) {
                        if(oldRoot.renameTo(newRoot)) {
                            renamed = true;
                            break; // succeeded
                        }
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // process the interruption later
                            interrupted = true;
                        }
                    }

                    if(interrupted)
                        Thread.currentThread().interrupt();

                    if(!renamed) {
                        // failed to rename. it must be that some lengthy process is going on
                        // to prevent a rename operation. So do a copy. Ideally we'd like to
                        // later delete the old copy, but we can't reliably do so, as before the VM
                        // shuts down there might be a new job created under the old name.
                        Copy cp = new Copy();
                        cp.setProject(new org.apache.tools.ant.Project());
                        cp.setTodir(newRoot);
                        FileSet src = new FileSet();
                        src.setDir(getRootDir());
                        cp.addFileset(src);
                        cp.setOverwrite(true);
                        cp.setPreserveLastModified(true);
                        cp.setFailOnError(false);   // keep going even if there's an error
                        cp.execute();

                        // try to delete as much as possible
                        try {
                            Util.deleteRecursive(oldRoot);
                        } catch (IOException e) {
                            // but ignore the error, since we expect that
                            e.printStackTrace();
                        }
                    }
                }

                parent.onRenamed(this,oldName,newName);

                // update BuildTrigger of other projects that point to this object.
                // can't we generalize this?
                for( Project p : parent.getProjects() ) {
                    BuildTrigger t = (BuildTrigger) p.getPublishers().get(BuildTrigger.DESCRIPTOR);
                    if(t!=null) {
                        if(t.onJobRenamed(oldName,newName))
                            p.save();
                    }
                }
            }
        }
    }

    /**
     * Returns true if we should display "build now" icon
     */
    public abstract boolean isBuildable();

    /**
     * Gets all the builds.
     *
     * @return
     *      never null. The first entry is the latest buildCommand.
     */
    public List<RunT> getBuilds() {
        return new ArrayList<RunT>(_getRuns().values());
    }

    /**
     * Gets all the builds in a map.
     */
    public SortedMap<Integer,RunT> getBuildsAsMap() {
        return Collections.unmodifiableSortedMap(_getRuns());
    }

    /**
     * @deprecated
     *      This is only used to support backward compatibility with
     *      old URLs.
     */
    @Deprecated
    public RunT getBuild(String id) {
        for (RunT r : _getRuns().values()) {
            if(r.getId().equals(id))
                return r;
        }
        return null;
    }

    /**
     * @param n
     *      The build number.
     * @see Run#getNumber()
     */
    public RunT getBuildByNumber(int n) {
        return _getRuns().get(n);
    }

    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        try {
            // try to interpret the token as build number
            return _getRuns().get(Integer.valueOf(token));
        } catch (NumberFormatException e) {
            return super.getDynamic(token,req,rsp);
        }
    }

    /**
     * The file we save our configuration.
     */
    protected static XmlFile getConfigFile(File dir) {
        return new XmlFile(XSTREAM,new File(dir,"config.xml"));
    }

    File getConfigFile() {
        return new File(root,"config.xml");
    }

    /**
     * Directory for storing {@link Run} records.
     * <p>
     * Some {@link Job}s may not have backing data store for {@link Run}s,
     * but those {@link Job}s that use file system for storing data
     * should use this directory for consistency.
     *
     * @see RunMap
     */
    protected File getBuildDir() {
        return new File(root,"builds");
    }

    /**
     * Returns the URL of this project.
     */
    public String getUrl() {
        return "job/"+name+'/';
    }

    /**
     * Gets all the runs.
     *
     * The resulting map must be immutable (by employing copy-on-write semantics.)
     */
    protected abstract SortedMap<Integer,? extends RunT> _getRuns();

    /**
     * Called from {@link Run} to remove it from this job.
     *
     * The files are deleted already. So all the callee needs to do
     * is to remove a reference from this {@link Job}.
     */
    protected abstract void removeRun(RunT run);

    /**
     * Returns the last build.
     */
    public RunT getLastBuild() {
        SortedMap<Integer,? extends RunT> runs = _getRuns();

        if(runs.isEmpty())    return null;
        return runs.get(runs.firstKey());
    }

    /**
     * Returns the oldest build in the record.
     */
    public RunT getFirstBuild() {
        SortedMap<Integer,? extends RunT> runs = _getRuns();

        if(runs.isEmpty())    return null;
        return runs.get(runs.lastKey());
    }

    /**
     * Returns the last successful build, if any. Otherwise null.
     */
    public RunT getLastSuccessfulBuild() {
        RunT r = getLastBuild();
        // temporary hack till we figure out what's causing this bug
        while(r!=null && (r.isBuilding() || r.getResult()==null || r.getResult().isWorseThan(Result.UNSTABLE)))
            r=r.getPreviousBuild();
        return r;
    }

    /**
     * Returns the last stable build, if any. Otherwise null.
     */
    public RunT getLastStableBuild() {
        RunT r = getLastBuild();
        while(r!=null && (r.isBuilding() || r.getResult().isWorseThan(Result.SUCCESS)))
            r=r.getPreviousBuild();
        return r;
    }

    /**
     * Returns the last failed build, if any. Otherwise null.
     */
    public RunT getLastFailedBuild() {
        RunT r = getLastBuild();
        while(r!=null && (r.isBuilding() || r.getResult()!=Result.FAILURE))
            r=r.getPreviousBuild();
        return r;
    }

    /**
     * Used as the color of the status ball for the project.
     */
    public BallColor getIconColor() {
        RunT lastBuild = getLastBuild();
        while(lastBuild!=null && lastBuild.hasntStartedYet())
            lastBuild = lastBuild.getPreviousBuild();

        if(lastBuild!=null)
            return lastBuild.getIconColor();
        else
            return BallColor.GREY;
    }


    /**
     * Save the settings to a file.
     */
    public synchronized void save() throws IOException {
        getConfigFile(root).write(this);
    }

    /**
     * Loads a project from a config file.
     */
    static Job load(Hudson root, File dir) throws IOException {
        Job job = (Job)getConfigFile(dir).read();
        job.onLoad(root,dir.getName());
        return job;
    }



//
//
// actions
//
//
    /**
     * Accepts submission from the configuration page.
     */
    public synchronized void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        req.setCharacterEncoding("UTF-8");

        description = req.getParameter("description");

        if(req.getParameter("logrotate")!=null)
            logRotator = LogRotator.DESCRIPTOR.newInstance(req);
        else
            logRotator = null;

        keepDependencies = req.getParameter("keepDependencies")!=null;

        save();

        String newName = req.getParameter("name");
        if(newName!=null && !newName.equals(name)) {
            rsp.sendRedirect("rename?newName="+newName);
        } else {
            rsp.sendRedirect(".");
        }
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        req.setCharacterEncoding("UTF-8");
        description = req.getParameter("description");
        save();
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Returns the image that shows the current buildCommand status.
     */
    public void doBuildStatus( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        rsp.sendRedirect2(req.getContextPath()+"/nocacheImages/48x48/"+getBuildStatusUrl());
    }

    public String getBuildStatusUrl() {
        return getIconColor()+".gif";
    }

    /**
     * Returns the graph that shows how long each build took.
     */
    public void doBuildTimeGraph( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        if(req.checkIfModified(getLastBuild().getTimestamp(),rsp))
            return;
        ChartUtil.generateGraph(req,rsp, createBuildTimeTrendChart(),500,400);
    }

    /**
     * Returns the clickable map for the build time graph.
     * Loaded lazily by AJAX.
     */
    public void doBuildTimeGraphMap( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        if(req.checkIfModified(getLastBuild().getTimestamp(),rsp))
            return;
        ChartUtil.generateClickableMap(req,rsp, createBuildTimeTrendChart(),500,400);
    }

    private JFreeChart createBuildTimeTrendChart() {
        class Label implements Comparable<Label> {
            final Run run;

            public Label(Run r) {
                this.run = r;
            }

            public int compareTo(Label that) {
                return this.run.number-that.run.number;
            }

            public boolean equals(Object o) {
                Label that = (Label) o;
                return run ==that.run;
            }

            public Color getColor() {
                // TODO: consider gradation. See http://www.javadrive.jp/java2d/shape/index9.html
                Result r = run.getResult();
                if(r ==Result.FAILURE || r== Result.ABORTED)
                    return ColorPalette.RED;
                else
                    return ColorPalette.BLUE;
            }

            public int hashCode() {
                return run.hashCode();
            }

            public String toString() {
                String l = run.getDisplayName();
                if(run instanceof Build) {
                    String s = ((Build)run).getBuiltOnStr();
                    if(s!=null)
                        l += ' '+s;
                }
                return l;
            }

        }

        DataSetBuilder<String,Label> data = new DataSetBuilder<String, Label>();
        for( Run r : getBuilds() )
            data.add( ((double)r.getDuration())/(1000*60), "mins", new Label(r));

        final CategoryDataset dataset = data.build();

        final JFreeChart chart = ChartFactory.createStackedAreaChart(
            null,                   // chart title
            null,                   // unused
            "min",                  // range axis label
            dataset,                  // data
            PlotOrientation.VERTICAL, // orientation
            false,                     // include legend
            true,                     // tooltips
            false                     // urls
        );

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
            public Paint getItemPaint(int row, int column) {
                Label key = (Label) dataset.getColumnKey(column);
                return key.getColor();
            }

            @Override
            public String generateURL(CategoryDataset dataset, int row, int column) {
                Label label = (Label) dataset.getColumnKey(column);
                return String.valueOf(label.run.number);
            }

            @Override
            public String generateToolTip(CategoryDataset dataset, int row, int column) {
                Label label = (Label) dataset.getColumnKey(column);
                return label.run.getDisplayName() + " : " + label.run.getDurationString();
            }
        };
        plot.setRenderer(ar);

        // crop extra space around the graph
        plot.setInsets(new RectangleInsets(0,0,0,5.0));

        return chart;
    }

    /**
     * Deletes this job.
     */
    public synchronized void doDoDelete( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        Util.deleteRecursive(root);
        getParent().deleteJob(this);
        rsp.sendRedirect2(req.getContextPath()+"/");
    }

    /**
     * Renames this job.
     */
    public /*not synchronized. see renameTo()*/ void doDoRename( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        String newName = req.getParameter("newName");

        renameTo(newName);
        rsp.sendRedirect2(req.getContextPath()+'/'+getUrl()); // send to the new job page
    }

    public void doRssAll( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " all builds", new RunList(this));
    }
    public void doRssFailed( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " failed builds", new RunList(this).failureOnly());
    }

    private void rss(StaplerRequest req, StaplerResponse rsp, String suffix, RunList runs) throws IOException, ServletException {
        RSS.forwardToRss(getDisplayName()+ suffix, getUrl(),
            runs.newBuilds(), Run.FEED_ADAPTER, req, rsp );
    }

    /**
     * Used to load/save job configuration.
     *
     * When you extend {@link Job} in a plugin, try to put the alias so
     * that it produces a reasonable XML.
     */
    protected static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("project",Project.class);
    }
}
