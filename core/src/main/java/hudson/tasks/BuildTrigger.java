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

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.console.ModelHyperlinkNote;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.AutoCompletionCandidates;
import hudson.model.BuildListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.DependencyGraph;
import hudson.model.DependencyGraph.Dependency;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import jenkins.model.DependencyDeclarer;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import jenkins.security.QueueItemAuthenticatorDescriptor;
import jenkins.triggers.ReverseBuildTrigger;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Triggers builds of other projects.
 *
 * <p>
 * Despite what the name suggests, this class doesn't actually trigger other jobs
 * as a part of {@link #perform} method. Its main job is to simply augment
 * {@link DependencyGraph}. Jobs are responsible for triggering downstream jobs
 * on its own, because dependencies may come from other sources.
 *
 * <p>
 * This class, however, does provide the {@link #execute(AbstractBuild, BuildListener, BuildTrigger)}
 * method as a convenience method to invoke downstream builds.
 *
 * <p>Its counterpart is {@link ReverseBuildTrigger}.
 *
 * @author Kohsuke Kawaguchi
 */
public class BuildTrigger extends Recorder implements DependencyDeclarer {

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

    public BuildTrigger(String childProjects, boolean evenIfUnstable) {
        this(childProjects,evenIfUnstable ? Result.UNSTABLE : Result.SUCCESS);
    }

    @DataBoundConstructor
    public BuildTrigger(String childProjects, String threshold) {
        this(childProjects, Result.fromString(StringUtils.defaultString(threshold, Result.SUCCESS.toString())));
    }

    public BuildTrigger(String childProjects, Result threshold) {
        if(childProjects==null)
            throw new IllegalArgumentException();
        this.childProjects = childProjects;
        this.threshold = threshold;
    }

    public BuildTrigger(List<AbstractProject> childProjects, Result threshold) {
        this((Collection<AbstractProject>)childProjects,threshold);
    }

    public BuildTrigger(Collection<? extends AbstractProject> childProjects, Result threshold) {
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

    /**
     * @deprecated as of 1.406
     *      Use {@link #getChildProjects(ItemGroup)}
     */
    @Deprecated
    public List<AbstractProject> getChildProjects() {
        return getChildProjects(Jenkins.getInstance());
    }

    public List<AbstractProject> getChildProjects(AbstractProject owner) {
        return getChildProjects(owner==null?null:owner.getParent());
    }

    public List<AbstractProject> getChildProjects(ItemGroup base) {
        return Items.fromNameList(base,childProjects,AbstractProject.class);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Checks if this trigger has the exact same set of children as the given list.
     */
    public boolean hasSame(AbstractProject owner, Collection<? extends AbstractProject> projects) {
        List<AbstractProject> children = getChildProjects(owner);
        return children.size()==projects.size() && children.containsAll(projects);
    }

    /**
     * @deprecated as of 1.406
     *      Use {@link #hasSame(AbstractProject, Collection)}
     */
    @Deprecated
    public boolean hasSame(Collection<? extends AbstractProject> projects) {
        return hasSame(null,projects);
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        return true;
    }

    /**
     * @deprecated since 1.341; use {@link #execute(AbstractBuild,BuildListener)}
     */
    @Deprecated
    public static boolean execute(AbstractBuild build, BuildListener listener, BuildTrigger trigger) {
        return execute(build, listener);
    }

    /**
     * Convenience method to trigger downstream builds.
     *
     * @param build
     *      The current build. Its downstreams will be triggered.
     * @param listener
     *      Receives the progress report.
     */
    public static boolean execute(AbstractBuild build, BuildListener listener) {
        PrintStream logger = listener.getLogger();
        // Check all downstream Project of the project, not just those defined by BuildTrigger
        // TODO this may not yet be up to date if rebuildDependencyGraphAsync has been used; need a method to wait for the last call made before now to finish
        final DependencyGraph graph = Jenkins.getInstance().getDependencyGraph();
        List<Dependency> downstreamProjects = new ArrayList<Dependency>(
                graph.getDownstreamDependencies(build.getProject()));
        // Sort topologically
        Collections.sort(downstreamProjects, new Comparator<Dependency>() {
            public int compare(Dependency lhs, Dependency rhs) {
                // Swapping lhs/rhs to get reverse sort:
                return graph.compare(rhs.getDownstreamProject(), lhs.getDownstreamProject());
            }
        });

        Authentication auth = Jenkins.getAuthentication(); // from build
        if (auth.equals(ACL.SYSTEM)) { // i.e., unspecified
            if (QueueItemAuthenticatorDescriptor.all().isEmpty()) {
                if (downstreamProjects.isEmpty()) {
                    return true;
                }
                logger.println(Messages.BuildTrigger_warning_you_have_no_plugins_providing_ac());
            } else if (QueueItemAuthenticatorConfiguration.get().getAuthenticators().isEmpty()) {
                if (downstreamProjects.isEmpty()) {
                    return true;
                }
                logger.println(Messages.BuildTrigger_warning_access_control_for_builds_in_glo());
            } else {
                // This warning must be printed even if downstreamProjects is empty.
                // Otherwise you could effectively escalate DISCOVER to READ just by trying different project names and checking whether a warning was printed or not.
                // If there were an API to determine whether any DependencyDeclarer’s in this project requested downstream project names,
                // then we could suppress the warnings in case none did; but if any do, yet Items.fromNameList etc. ignore unknown projects,
                // that has to be treated the same as if there really are downstream projects but the anonymous user cannot see them.
                // For the above two cases, it is OK to suppress the warning when there are no downstream projects, since running as SYSTEM we would be able to see them anyway.
                logger.println(Messages.BuildTrigger_warning_this_build_has_no_associated_aut());
                auth = Jenkins.ANONYMOUS;
            }
        }

        for (Dependency dep : downstreamProjects) {
            List<Action> buildActions = new ArrayList<Action>();
            SecurityContext orig = ACL.impersonate(auth);
            try {
                if (dep.shouldTriggerBuild(build, listener, buildActions)) {
                    AbstractProject p = dep.getDownstreamProject();
                    // Allow shouldTriggerBuild to return false first, in case it is skipping because of a lack of Item.READ/DISCOVER permission:
                    if (p.isDisabled()) {
                        logger.println(Messages.BuildTrigger_Disabled(ModelHyperlinkNote.encodeTo(p)));
                        continue;
                    }
                    boolean scheduled = p.scheduleBuild(p.getQuietPeriod(), new UpstreamCause((Run)build), buildActions.toArray(new Action[buildActions.size()]));
                    if (Jenkins.getInstance().getItemByFullName(p.getFullName()) == p) {
                        String name = ModelHyperlinkNote.encodeTo(p);
                        if (scheduled) {
                            logger.println(Messages.BuildTrigger_Triggering(name));
                        } else {
                            logger.println(Messages.BuildTrigger_InQueue(name));
                        }
                    } // otherwise upstream users should not know that it happened
                }
            } finally {
                SecurityContextHolder.setContext(orig);
            }
        }

        return true;
    }

    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
        for (AbstractProject p : getChildProjects(owner))
            graph.addDependency(new Dependency(owner, p) {
                @Override
                public boolean shouldTriggerBuild(AbstractBuild build, TaskListener listener,
                                                  List<Action> actions) {
                    AbstractProject downstream = getDownstreamProject();
                    if (Jenkins.getInstance().getItemByFullName(downstream.getFullName()) != downstream) { // this checks Item.READ also on parent folders
                        LOGGER.log(Level.WARNING, "Running as {0} cannot even see {1} for trigger from {2}", new Object[] {Jenkins.getAuthentication().getName(), downstream, getUpstreamProject()});
                        return false; // do not even issue a warning to build log
                    }
                    if (!downstream.hasPermission(Item.BUILD)) {
                        listener.getLogger().println(Messages.BuildTrigger_you_have_no_permission_to_build_(ModelHyperlinkNote.encodeTo(downstream)));
                        return false;
                    }
                    return build.getResult().isBetterOrEqualTo(threshold);
                }
            });
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    /** @deprecated Does not handle folder moves. */
    @Deprecated
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

        @Override
        public String getHelpFile() {
            return "/help/project-config/downstream.html";
        }

        @Override
        public BuildTrigger newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String childProjectsString = formData.getString("childProjects").trim();
            if (childProjectsString.endsWith(",")) {
                childProjectsString = childProjectsString.substring(0, childProjectsString.length() - 1).trim();
            }
            return new BuildTrigger(
                childProjectsString,
                formData.optString("threshold", Result.SUCCESS.toString()));
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public boolean showEvenIfUnstableOption(@CheckForNull Class<? extends AbstractProject<?,?>> jobType) {
            // UGLY: for promotion process, this option doesn't make sense.
            return jobType == null || !jobType.getName().contains("PromotionProcess");
        }

        /**
         * Form validation method.
         */
        public FormValidation doCheck(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            // Require CONFIGURE permission on this project
            if(!project.hasPermission(Item.CONFIGURE))      return FormValidation.ok();

            StringTokenizer tokens = new StringTokenizer(Util.fixNull(value),",");
            boolean hasProjects = false;
            while(tokens.hasMoreTokens()) {
                String projectName = tokens.nextToken().trim();
                if (StringUtils.isNotBlank(projectName)) {
                    Item item = Jenkins.getInstance().getItem(projectName,project,Item.class);
                    if (item == null) {
                        AbstractProject nearest = AbstractProject.findNearest(projectName,project.getParent());
                        String alternative = nearest != null ? nearest.getRelativeNameFrom(project) : "?";
                        return FormValidation.error(Messages.BuildTrigger_NoSuchProject(projectName, alternative));
                    }
                    if(!(item instanceof AbstractProject))
                        return FormValidation.error(Messages.BuildTrigger_NotBuildable(projectName));
                    // check whether the supposed user is expected to be able to build
                    Authentication auth = Tasks.getAuthenticationOf(project);
                    if (auth.equals(ACL.SYSTEM) && !QueueItemAuthenticatorConfiguration.get().getAuthenticators().isEmpty()) {
                        auth = Jenkins.ANONYMOUS; // compare behavior in execute, above
                    }
                    if (!item.getACL().hasPermission(auth, Item.BUILD)) {
                        return FormValidation.error(Messages.BuildTrigger_you_have_no_permission_to_build_(projectName));
                    }
                    hasProjects = true;
                }
            }
            if (!hasProjects) {
                return FormValidation.error(Messages.BuildTrigger_NoProjectSpecified());
            }

            return FormValidation.ok();
        }

        public AutoCompletionCandidates doAutoCompleteChildProjects(@QueryParameter String value, @AncestorInPath Item self, @AncestorInPath ItemGroup container) {
            return AutoCompletionCandidates.ofJobNames(Job.class,value,self,container);
        }

        @Extension
        public static class ItemListenerImpl extends ItemListener {
            @Override
            public void onLocationChanged(final Item item, final String oldFullName, final String newFullName) {
                ACL.impersonate(ACL.SYSTEM, new Runnable() {
                    @Override public void run() {
                        locationChanged(item, oldFullName, newFullName);
                    }
                });
            }
            private void locationChanged(Item item, String oldFullName, String newFullName) {
                // update BuildTrigger of other projects that point to this object.
                // can't we generalize this?
                for( Project<?,?> p : Jenkins.getInstance().getAllItems(Project.class) ) {
                    BuildTrigger t = p.getPublishersList().get(BuildTrigger.class);
                    if(t!=null) {
                        String cp2 = Items.computeRelativeNamesAfterRenaming(oldFullName, newFullName, t.childProjects, p);
                        if (!cp2.equals(t.childProjects)) {
                            t.childProjects = cp2;
                            try {
                                p.save();
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "Failed to persist project setting during rename from "+oldFullName+" to "+newFullName,e);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(BuildTrigger.class.getName());
}
