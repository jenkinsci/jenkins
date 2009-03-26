/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Martin Eigenbrodt
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
package hudson.tasks;

import hudson.Launcher;
import hudson.Extension;
import hudson.Util;
import hudson.security.AccessControlled;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.DependecyDeclarer;
import hudson.model.DependencyGraph;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.Cause.UpstreamCause;
import hudson.model.listeners.ItemListener;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Triggers builds of other projects.
 *
 * <p>
 * Despite what the name suggests, this class doesn't actually trigger other jobs
 * as a part of {@link #perform} method. Its main job is to simply augument
 * {@link DependencyGraph}. Jobs are responsible for triggering downstream jobs
 * on its own, because dependencies may come from other sources.
 *
 * <p>
 * This class, however, does provide the {@link #execute(AbstractBuild, BuildListener, BuildTrigger)}
 * method as a convenience method to invoke downstream builds.
 *
 * @author Kohsuke Kawaguchi
 */
public class BuildTrigger extends Recorder implements DependecyDeclarer, MatrixAggregatable {

    /**
     * Comma-separated list of other projects to be scheduled.
     */
    private String childProjects;

    /**
     * Threshold status to trigger other builds.
     *
     * For compatibility reasons, this field could be null, in which case
     * it should read as "SUCCESS".
     */
    private final Result threshold;

    @DataBoundConstructor
    public BuildTrigger(String childProjects, boolean evenIfUnstable) {
        this(childProjects,evenIfUnstable ? Result.UNSTABLE : Result.SUCCESS);
    }

    public BuildTrigger(String childProjects, Result threshold) {
        if(childProjects==null)
            throw new IllegalArgumentException();
        this.childProjects = childProjects;
        this.threshold = threshold;
    }

    public BuildTrigger(List<AbstractProject> childProjects, Result threshold) {
        this(Items.toNameList(childProjects),threshold);
    }

    public String getChildProjectsValue() {
        return childProjects;
    }

    public Result getThreshold() {
        if(threshold==null)
            return Result.SUCCESS;
        else
            return threshold;
    }

    public List<AbstractProject> getChildProjects() {
        return Items.fromNameList(childProjects,AbstractProject.class);
    }

    /**
     * Checks if this trigger has the exact same set of children as the given list.
     */
    public boolean hasSame(Collection<? extends AbstractProject> projects) {
        List<AbstractProject> children = getChildProjects();
        return children.size()==projects.size() && children.containsAll(projects);
    }

    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        return true;
    }

    /**
     * Convenience method to trigger downstream builds.
     *
     * @param build
     *      The current build. Its downstreams will be triggered.
     * @param listener
     *      Receives the progress report.
     * @param trigger
     *      Optional {@link BuildTrigger} configured for the current build.
     *      If it is non-null, its configuration value will affect the triggering behavior.
     *      But even when this is null (meaning no user-defined downstream project is set up),
     *      there might be other dependencies defined by somebody else, so buidl would
     *      still have to call this method. 
     */
    public static boolean execute(AbstractBuild build, BuildListener listener, BuildTrigger trigger) {
        if(trigger==null || !build.getResult().isWorseThan(trigger.getThreshold())) {
            PrintStream logger = listener.getLogger();
            //Trigger all downstream Project of the project, not just those defined by this buildtrigger
            List <AbstractProject> downstreamProjects = 
                new ArrayList<AbstractProject> (build.getProject().getDownstreamProjects()); 
                
            // Sort topologically            
            Collections.sort(downstreamProjects, 
                    Collections.reverseOrder (Hudson.getInstance().getDependencyGraph()));
            
            for (AbstractProject p : downstreamProjects) {
                if(p.isDisabled()) {
                    logger.println(Messages.BuildTrigger_Disabled(p.getName()));
                    continue;
                }
                // this is not completely accurate, as a new build might be triggered
                // between these calls
                String name = p.getName()+" #"+p.getNextBuildNumber();
                if(p.scheduleBuild(new UpstreamCause(build))) {
                    logger.println(Messages.BuildTrigger_Triggering(name));
                } else {
                    logger.println(Messages.BuildTrigger_InQueue(name));
                }
            }
        }

        return true;
    }

    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
        graph.addDependency(owner,getChildProjects());
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new MatrixAggregator(build, launcher, listener) {
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                return execute(build,listener,BuildTrigger.this);
            }
        };
    }

    /**
     * Called from {@link Job#renameTo(String)} when a job is renamed.
     *
     * @return true
     *      if this {@link BuildTrigger} is changed and needs to be saved.
     */
    public boolean onJobRenamed(String oldName, String newName) {
        // quick test
        if(!childProjects.contains(oldName))
            return false;

        boolean changed = false;

        // we need to do this per string, since old Project object is already gone.
        String[] projects = childProjects.split(",");
        for( int i=0; i<projects.length; i++ ) {
            if(projects[i].trim().equals(oldName)) {
                projects[i] = newName;
                changed = true;
            }
        }

        if(changed) {
            StringBuilder b = new StringBuilder();
            for (String p : projects) {
                if(b.length()>0)    b.append(',');
                b.append(p);
            }
            childProjects = b.toString();
        }

        return changed;
    }

    /**
     * Correct broken data gracefully (#1537)
     */
    private Object readResolve() {
        if(childProjects==null)
            return childProjects="";
        return this;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public String getDisplayName() {
            return Messages.BuildTrigger_DisplayName();
        }

        public String getHelpFile() {
            return "/help/project-config/downstream.html";
        }

        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new BuildTrigger(
                formData.getString("childProjects"),
                formData.has("evenIfUnstable") && formData.getBoolean("evenIfUnstable"));
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public boolean showEvenIfUnstableOption(Class<? extends AbstractProject> jobType) {
            // UGLY: for promotion process, this option doesn't make sense. 
            return !jobType.getName().contains("PromotionProcess");
        }

        /**
         * Form validation method.
         */
        public FormValidation doCheck(@AncestorInPath AccessControlled subject, @QueryParameter String value ) {
            // Require CONFIGURE permission on this project
            if(!subject.hasPermission(Item.CONFIGURE))      return FormValidation.ok();

            StringTokenizer tokens = new StringTokenizer(Util.fixNull(value),",");
            while(tokens.hasMoreTokens()) {
                String projectName = tokens.nextToken().trim();
                Item item = Hudson.getInstance().getItemByFullName(projectName,Item.class);
                if(item==null)
                    return FormValidation.error(Messages.BuildTrigger_NoSuchProject(projectName,AbstractProject.findNearest(projectName).getName()));
                if(!(item instanceof AbstractProject))
                    return FormValidation.error(Messages.BuildTrigger_NotBuildable(projectName));
            }

            return FormValidation.ok();
        }

        @Extension
        public static class ItemListenerImpl extends ItemListener {
            @Override
            public void onRenamed(Item item, String oldName, String newName) {
                // update BuildTrigger of other projects that point to this object.
                // can't we generalize this?
                for( Project<?,?> p : Hudson.getInstance().getProjects() ) {
                    BuildTrigger t = p.getPublishersList().get(BuildTrigger.class);
                    if(t!=null) {
                        if(t.onJobRenamed(oldName,newName)) {
                            try {
                                p.save();
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "Failed to persist project setting during rename from "+oldName+" to "+newName,e);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(BuildTrigger.class.getName());
}
