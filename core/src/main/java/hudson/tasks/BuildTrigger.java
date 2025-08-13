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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import hudson.security.ACLContext;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.DependencyDeclarer;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.triggers.ReverseBuildTrigger;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.springframework.security.core.Authentication;

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
        this(childProjects, evenIfUnstable ? Result.UNSTABLE : Result.SUCCESS);
    }

    @DataBoundConstructor
    public BuildTrigger(String childProjects, String threshold) {
        this(childProjects, Result.fromString(Objects.toString(threshold, Result.SUCCESS.toString())));
    }

    public BuildTrigger(String childProjects, Result threshold) {
        if (childProjects == null)
            throw new IllegalArgumentException();
        this.childProjects = childProjects;
        this.threshold = threshold;
    }

    public BuildTrigger(List<AbstractProject> childProjects, Result threshold) {
        this((Collection<AbstractProject>) childProjects, threshold);
    }

    public BuildTrigger(Collection<? extends AbstractProject> childProjects, Result threshold) {
        this(Items.toNameList(childProjects), threshold);
    }

    public String getChildProjectsValue() {
        return childProjects;
    }

    public Result getThreshold() {
        if (threshold == null)
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
        return getChildProjects(Jenkins.get());
    }

    /** @deprecated use {@link #getChildJobs} */
    @Deprecated
    public List<AbstractProject> getChildProjects(AbstractProject owner) {
        return getChildProjects(owner == null ? null : owner.getParent());
    }

    @Deprecated
    public List<AbstractProject> getChildProjects(ItemGroup base) {
        return Items.fromNameList(base, childProjects, AbstractProject.class);
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public List<Job<?, ?>> getChildJobs(@NonNull AbstractProject<?, ?> owner) {
        return Items.fromNameList(owner.getParent(), childProjects, (Class<Job<?, ?>>) (Class) Job.class);
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * @deprecated apparently unused
     */
    @Deprecated
    public boolean hasSame(AbstractProject owner, Collection<? extends AbstractProject> projects) {
        List<AbstractProject> children = getChildProjects(owner);
        return children.size() == projects.size() && children.containsAll(projects);
    }

    /**
     * @deprecated as of 1.406
     *      Use {@link #hasSame(AbstractProject, Collection)}
     */
    @Deprecated
    public boolean hasSame(Collection<? extends AbstractProject> projects) {
        return hasSame(null, projects);
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        List<Job<?, ?>> jobs = new ArrayList<>();
        for (Job<?, ?> job : getChildJobs(build.getProject())) {
            if (job instanceof AbstractProject) {
                continue; // taken care of by DependencyGraph
            }
            jobs.add(job);
        }
        if (!jobs.isEmpty() && build.getResult().isBetterOrEqualTo(threshold)) {
            PrintStream logger = listener.getLogger();
            for (Job<?, ?> downstream : jobs) {
                if (Jenkins.get().getItemByFullName(downstream.getFullName()) != downstream) {
                    LOGGER.log(Level.WARNING, "Running as {0} cannot even see {1} for trigger from {2}", new Object[] {Jenkins.getAuthentication2().getName(), downstream, build.getParent()});
                    continue;
                }
                if (!downstream.hasPermission(Item.BUILD)) {
                    listener.getLogger().println(Messages.BuildTrigger_you_have_no_permission_to_build_(ModelHyperlinkNote.encodeTo(downstream)));
                    continue;
                }
                if (!(downstream instanceof ParameterizedJobMixIn.ParameterizedJob)) {
                    logger.println(Messages.BuildTrigger_NotBuildable(ModelHyperlinkNote.encodeTo(downstream)));
                    continue;
                }
                ParameterizedJobMixIn.ParameterizedJob<?, ?> pj = (ParameterizedJobMixIn.ParameterizedJob) downstream;
                if (pj.isDisabled()) {
                    logger.println(Messages.BuildTrigger_Disabled(ModelHyperlinkNote.encodeTo(downstream)));
                    continue;
                }
                if (!downstream.isBuildable()) { // some other reason; no API to retrieve cause
                    logger.println(Messages.BuildTrigger_NotBuildable(ModelHyperlinkNote.encodeTo(downstream)));
                    continue;
                }
                boolean scheduled = pj.scheduleBuild(pj.getQuietPeriod(), new UpstreamCause((Run) build));
                if (Jenkins.get().getItemByFullName(downstream.getFullName()) == downstream) {
                    String name = ModelHyperlinkNote.encodeTo(downstream);
                    if (scheduled) {
                        logger.println(Messages.BuildTrigger_Triggering(name));
                    } else {
                        logger.println(Messages.BuildTrigger_InQueue(name));
                    }
                }
            }
        }
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

        DependencyGraph graphTemp;
        try {
            // Note: futureDependencyGraph can be null, if no asynchronous computation of the
            // dependency graph has been performed.
            Future<DependencyGraph> futureDependencyGraph = Jenkins.get().getFutureDependencyGraph();
            if (futureDependencyGraph != null) {
                graphTemp = futureDependencyGraph.get();
            } else {
                graphTemp = Jenkins.get().getDependencyGraph();
            }
        } catch (IllegalStateException | InterruptedException | ExecutionException e) {
            // Use old version of dependency graph instead
            graphTemp = Jenkins.get().getDependencyGraph();
        }
        DependencyGraph graph = graphTemp;

        List<Dependency> downstreamProjects = new ArrayList<>(
                graph.getDownstreamDependencies(build.getProject()));
        // Sort topologically
        downstreamProjects.sort(new Comparator<>() {
            @Override
            public int compare(Dependency lhs, Dependency rhs) {
                // Swapping lhs/rhs to get reverse sort:
                return graph.compare(rhs.getDownstreamProject(), lhs.getDownstreamProject());
            }
        });

        for (Dependency dep : downstreamProjects) {
            List<Action> buildActions = new ArrayList<>();
            if (dep.shouldTriggerBuild(build, listener, buildActions)) {
                AbstractProject p = dep.getDownstreamProject();
                // Allow shouldTriggerBuild to return false first, in case it is skipping because of a lack of Item.READ/DISCOVER permission:
                if (p.isDisabled()) {
                    logger.println(Messages.BuildTrigger_Disabled(ModelHyperlinkNote.encodeTo(p)));
                    continue;
                }
                boolean scheduled = p.scheduleBuild(p.getQuietPeriod(), new UpstreamCause((Run) build), buildActions.toArray(new Action[0]));
                if (Jenkins.get().getItemByFullName(p.getFullName()) == p) {
                    String name = ModelHyperlinkNote.encodeTo(p);
                    if (scheduled) {
                        logger.println(Messages.BuildTrigger_Triggering(name));
                    } else {
                        logger.println(Messages.BuildTrigger_InQueue(name));
                    }
                } // otherwise upstream users should not know that it happened
            }
        }

        return true;
    }

    @Override
    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
        for (AbstractProject p : getChildProjects(owner)) // only care about AbstractProject here
            graph.addDependency(new Dependency(owner, p) {
                @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "TODO needs triage")
                @Override
                public boolean shouldTriggerBuild(AbstractBuild build, TaskListener listener,
                                                  List<Action> actions) {
                    AbstractProject downstream = getDownstreamProject();
                    if (Jenkins.get().getItemByFullName(downstream.getFullName()) != downstream) { // this checks Item.READ also on parent folders
                        LOGGER.log(Level.WARNING, "Running as {0} cannot even see {1} for trigger from {2}", new Object[] {Jenkins.getAuthentication2().getName(), downstream, getUpstreamProject()});
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
        if (!childProjects.contains(oldName))
            return false;

        boolean changed = false;

        // we need to do this per string, since old Project object is already gone.
        String[] projects = childProjects.split(",");
        for (int i = 0; i < projects.length; i++) {
            if (projects[i].trim().equals(oldName)) {
                projects[i] = newName;
                changed = true;
            }
        }

        if (changed) {
            childProjects = String.join(",", projects);
        }

        return changed;
    }

    @Extension @Symbol("downstream")
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.BuildTrigger_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/project-config/downstream.html";
        }

        @Override
        public BuildTrigger newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
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

        public boolean showEvenIfUnstableOption(@CheckForNull Class<? extends AbstractProject<?, ?>> jobType) {
            // UGLY: for promotion process, this option doesn't make sense.
            return jobType == null || !jobType.getName().contains("PromotionProcess");
        }

        /**
         * Form validation method.
         */
        public FormValidation doCheck(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            // JENKINS-32525: Check that it behaves gracefully for an unknown context
            if (project == null) return FormValidation.ok(Messages.BuildTrigger_ok_ancestor_is_null());
            // Require CONFIGURE permission on this project
            if (!project.hasPermission(Item.CONFIGURE))      return FormValidation.ok();

            StringTokenizer tokens = new StringTokenizer(Util.fixNull(value), ",");
            boolean hasProjects = false;
            while (tokens.hasMoreTokens()) {
                String projectName = tokens.nextToken().trim();
                if (projectName != null && !projectName.isBlank()) {
                    Item item = Jenkins.get().getItem(projectName, project, Item.class);
                    if (item == null) {
                        Job<?, ?> nearest = Items.findNearest(Job.class, projectName, project.getParent());
                        String alternative = nearest != null ? nearest.getRelativeNameFrom(project) : "?";
                        return FormValidation.error(Messages.BuildTrigger_NoSuchProject(projectName, alternative));
                    }
                    if (!(item instanceof ParameterizedJobMixIn.ParameterizedJob))
                        return FormValidation.error(Messages.BuildTrigger_NotBuildable(projectName));
                    // check whether the supposed user is expected to be able to build
                    Authentication auth = Tasks.getAuthenticationOf2(project);
                    if (!item.hasPermission2(auth, Item.BUILD)) {
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
            return AutoCompletionCandidates.ofJobNames(Job.class, value, self, container);
        }

        @Extension
        public static class ItemListenerImpl extends ItemListener {
            @Override
            public void onLocationChanged(final Item item, final String oldFullName, final String newFullName) {
                try (ACLContext acl = ACL.as2(ACL.SYSTEM2)) {
                    locationChanged(item, oldFullName, newFullName);
                }
            }

            private void locationChanged(Item item, String oldFullName, String newFullName) {
                // update BuildTrigger of other projects that point to this object.
                // can't we generalize this?
                for (Project<?, ?> p : Jenkins.get().allItems(Project.class)) {
                    BuildTrigger t = p.getPublishersList().get(BuildTrigger.class);
                    if (t != null) {
                        String cp2 = Items.computeRelativeNamesAfterRenaming(oldFullName, newFullName, t.childProjects, p.getParent());
                        if (!cp2.equals(t.childProjects)) {
                            t.childProjects = cp2;
                            try {
                                p.save();
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "Failed to persist project setting during rename from " + oldFullName + " to " + newFullName, e);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(BuildTrigger.class.getName());
}
