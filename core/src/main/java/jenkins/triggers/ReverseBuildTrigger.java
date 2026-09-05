/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package jenkins.triggers;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.console.ModelHyperlinkNote;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.DependencyGraph;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.tasks.BuildTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.DependencyDeclarer;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/**
 * Like {@link BuildTrigger} but defined on the downstream project.
 * Operates via {@link BuildTrigger#execute} and {@link DependencyGraph},
 * so run implicitly at the end of the upstream build,
 * when used on a pair of {@link AbstractProject}s.
 * Otherwise directly listens for the upstream build to complete.
 * @since 1.560
 */
@SuppressWarnings("rawtypes")
public final class ReverseBuildTrigger extends Trigger<Job> implements DependencyDeclarer {

    private static final Logger LOGGER = Logger.getLogger(ReverseBuildTrigger.class.getName());

    @CheckForNull
    private String upstreamProjects;
    private Result threshold = Result.SUCCESS;

    /**
     * Legacy constructor used before {@link #threshold} was moved to a {@code @DataBoundSetter}. Kept around for binary
     * compatibility.
     * @deprecated use {@link #ReverseBuildTrigger(String)} and {@link #setThreshold(Result)}
     */
    @Deprecated
    public ReverseBuildTrigger(String upstreamProjects, Result threshold) {
        this(upstreamProjects);
        this.threshold = threshold;
    }

    @DataBoundConstructor
    public ReverseBuildTrigger(String upstreamProjects) {
        this.upstreamProjects = upstreamProjects;
    }

    /**
     * Gets the upstream projects.
     *
     * @return Upstream projects or empty("") if upstream projects is null.
     */
    public String getUpstreamProjects() {
        return Util.fixNull(upstreamProjects);
    }

    public Result getThreshold() {
        return threshold;
    }

    @DataBoundSetter
    public void setThreshold(Result r) {
        this.threshold = r;
    }

    private boolean shouldTrigger(Run upstreamBuild, TaskListener listener) {
        Jenkins jenkins = Jenkins.get();
        if (job == null) {
            return false;
        }

        boolean downstreamVisible = false;
        boolean downstreamDiscoverable = false;

        // This checks Item.READ also on parent folders; note we are checking as the upstream auth currently:
        try {
            downstreamVisible = jenkins.getItemByFullName(job.getFullName()) == job;
        } catch (AccessDeniedException ex) {
            // Fails because of missing Item.READ but upstream user has Item.DISCOVER
            downstreamDiscoverable = true;
        }

        Authentication originalAuth = Jenkins.getAuthentication2();
        Job upstream = upstreamBuild.getParent();
        Authentication auth = Tasks.getAuthenticationOf2((Queue.Task) job);

        Item authUpstream = null;
        try (ACLContext ctx = ACL.as2(auth)) {
            authUpstream = jenkins.getItemByFullName(upstream.getFullName());
            // No need to check Item.BUILD on downstream, because the downstream project’s configurer has asked for this.
        } catch (AccessDeniedException ade) {
            // Fails because of missing Item.READ but downstream user has Item.DISCOVER
        }

        if (authUpstream != upstream) {
            if (downstreamVisible) {
                // TODO ModelHyperlink
                listener.getLogger().println(Messages.ReverseBuildTrigger_running_as_cannot_even_see_for_trigger_f(auth.getName(),
                        upstream.getFullName(), job.getFullName()));
            } else  {
                LOGGER.log(Level.WARNING, "Running as {0} cannot even {1} {2} for trigger from {3}, (but cannot tell {4} that)",
                        new Object [] { auth.getName(), downstreamDiscoverable ? "READ" : "DISCOVER", upstream, job, originalAuth.getName()});
            }
            return false;
        }
        Result result = upstreamBuild.getResult();
        return result != null && result.isBetterOrEqualTo(threshold != null ? threshold : Result.SUCCESS);
    }

    @Override public void buildDependencyGraph(final AbstractProject downstream, DependencyGraph graph) {
        for (AbstractProject upstream : Items.fromNameList(downstream.getParent(), getUpstreamProjects(), AbstractProject.class)) {
            graph.addDependency(new DependencyGraph.Dependency(upstream, downstream) {
                @Override public boolean shouldTriggerBuild(AbstractBuild upstreamBuild, TaskListener listener, List<Action> actions) {
                    return shouldTrigger(upstreamBuild, listener);
                }
            });
        }
    }

    @Override public void start(@NonNull Job project, boolean newInstance) {
        super.start(project, newInstance);
        RunListenerImpl.get().invalidateCache();
    }

    @Override public void stop() {
        super.stop();
        RunListenerImpl.get().invalidateCache();
    }

    @Extension @Symbol("upstream")
    public static final class DescriptorImpl extends TriggerDescriptor {

        @NonNull
        @Override public String getDisplayName() {
            return Messages.ReverseBuildTrigger_build_after_other_projects_are_built();
        }

        @Override public boolean isApplicable(Item item) {
            return item instanceof Job && item instanceof ParameterizedJobMixIn.ParameterizedJob;
        }

        public AutoCompletionCandidates doAutoCompleteUpstreamProjects(@QueryParameter String value, @AncestorInPath Item self, @AncestorInPath ItemGroup container) {
            return AutoCompletionCandidates.ofJobNames(Job.class, value, self, container);
        }

        public FormValidation doCheckUpstreamProjects(@AncestorInPath Job project, @QueryParameter String value) {
            if (!project.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }
            StringTokenizer tokens = new StringTokenizer(Util.fixNull(value), ",");
            boolean hasProjects = false;
            while (tokens.hasMoreTokens()) {
                String projectName = tokens.nextToken().trim();
                if (projectName != null && !projectName.isBlank()) {
                    Job item = Jenkins.get().getItem(projectName, project, Job.class);
                    if (item == null) {
                        Job nearest = Items.findNearest(Job.class, projectName, project.getParent());
                        String alternative = nearest != null ? nearest.getRelativeNameFrom(project) : "?";
                        return FormValidation.error(hudson.tasks.Messages.BuildTrigger_NoSuchProject(projectName, alternative));
                    }
                    hasProjects = true;
                }
            }
            if (!hasProjects) {
                return FormValidation.error(hudson.tasks.Messages.BuildTrigger_NoProjectSpecified());
            }

            return FormValidation.ok();
        }

    }

    @Extension public static final class RunListenerImpl extends RunListener<Run> {

        static RunListenerImpl get() {
            return ExtensionList.lookupSingleton(RunListenerImpl.class);
        }

        private Map<Job, Collection<ReverseBuildTrigger>> upstream2Trigger;

        synchronized void invalidateCache() {
            upstream2Trigger = null;
        }

        private Map<Job, Collection<ReverseBuildTrigger>> calculateCache() {
            try (ACLContext acl = ACL.as2(ACL.SYSTEM2)) {
                final Map<Job, Collection<ReverseBuildTrigger>> result = new WeakHashMap<>();
                for (Job<?, ?> downstream : Jenkins.get().allItems(Job.class)) {
                    ReverseBuildTrigger trigger =
                            ParameterizedJobMixIn.getTrigger(downstream, ReverseBuildTrigger.class);
                    if (trigger == null) {
                        continue;
                    }
                    List<Job> upstreams =
                            Items.fromNameList(downstream.getParent(), trigger.getUpstreamProjects(), Job.class);
                    LOGGER.log(Level.FINE, "from {0} see upstreams {1}", new Object[]{downstream, upstreams});
                    for (Job upstream : upstreams) {
                        if (upstream instanceof AbstractProject && downstream instanceof AbstractProject) {
                            continue; // handled specially
                        }
                        Collection<ReverseBuildTrigger> triggers = result.computeIfAbsent(upstream, k -> new ArrayList<>());
                        triggers.remove(trigger);
                        triggers.add(trigger);
                    }
                }
                return result;
            }
        }

        @Override public void onCompleted(@NonNull Run r, @NonNull TaskListener listener) {
            Collection<ReverseBuildTrigger> triggers;
            synchronized (this) {
                if (upstream2Trigger == null) {
                    upstream2Trigger = calculateCache();
                }
                Collection<ReverseBuildTrigger> _triggers = upstream2Trigger.get(r.getParent());
                if (_triggers == null || _triggers.isEmpty()) {
                    return;
                }
                triggers = new ArrayList<>(_triggers);
            }
            for (final ReverseBuildTrigger trigger : triggers) {
                if (trigger.shouldTrigger(r, listener)) {
                    // Make SpotBugs happy.
                    if (trigger.job == null) {
                        continue;
                    }
                    if (!trigger.job.isBuildable()) {
                        listener.getLogger().println(hudson.tasks.Messages.BuildTrigger_Disabled(ModelHyperlinkNote.encodeTo(trigger.job)));
                        continue;
                    }
                    String name = ModelHyperlinkNote.encodeTo(trigger.job) + " #" + trigger.job.getNextBuildNumber();
                    if (ParameterizedJobMixIn.scheduleBuild2(trigger.job, -1, new CauseAction(new Cause.UpstreamCause(r))) != null) {
                        listener.getLogger().println(hudson.tasks.Messages.BuildTrigger_Triggering(name));
                    } else {
                        listener.getLogger().println(hudson.tasks.Messages.BuildTrigger_InQueue(name));
                    }
                }
            }
        }
    }

    @Extension
    public static class ItemListenerImpl extends ItemListener {
        @Override
        public void onLocationChanged(Item item, final String oldFullName, final String newFullName) {
            try (ACLContext acl = ACL.as2(ACL.SYSTEM2)) {
                for (Job<?, ?> p : Jenkins.get().allItems(Job.class)) {
                    ReverseBuildTrigger t = ParameterizedJobMixIn.getTrigger(p, ReverseBuildTrigger.class);
                    if (t != null) {
                        String revised =
                                Items.computeRelativeNamesAfterRenaming(oldFullName, newFullName,
                                        t.getUpstreamProjects(), p.getParent());
                        if (!revised.equals(t.upstreamProjects)) {
                            t.upstreamProjects = revised;
                            try {
                                p.save();
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING,
                                        "Failed to persist project setting during rename from " + oldFullName + " to "
                                                + newFullName, e);
                            }
                        }
                    }
                }
            }
        }
    }
}
