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

import hudson.Extension;
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
import hudson.tasks.BuildTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.DependencyDeclarer;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;

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
    private static final Map<Job,Collection<ReverseBuildTrigger>> upstream2Trigger = new WeakHashMap<>();

    private String upstreamProjects;
    private final Result threshold;

    @DataBoundConstructor
    public ReverseBuildTrigger(String upstreamProjects, Result threshold) {
        this.upstreamProjects = upstreamProjects;
        this.threshold = threshold;
    }

    public String getUpstreamProjects() {
        return upstreamProjects;
    }

    public Result getThreshold() {
        return threshold;
    }

    private boolean shouldTrigger(Run upstreamBuild, TaskListener listener) {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return false;
        }
        // This checks Item.READ also on parent folders; note we are checking as the upstream auth currently:
        boolean downstreamVisible = jenkins.getItemByFullName(job.getFullName()) == job;
        Authentication originalAuth = Jenkins.getAuthentication();
        Job upstream = upstreamBuild.getParent();
        Authentication auth = Tasks.getAuthenticationOf((Queue.Task) job);
        if (auth.equals(ACL.SYSTEM) && !QueueItemAuthenticatorConfiguration.get().getAuthenticators().isEmpty()) {
            auth = Jenkins.ANONYMOUS; // cf. BuildTrigger
        }
        SecurityContext orig = ACL.impersonate(auth);
        try {
            if (jenkins.getItemByFullName(upstream.getFullName()) != upstream) {
                if (downstreamVisible) {
                    // TODO ModelHyperlink
                    listener.getLogger().println(Messages.ReverseBuildTrigger_running_as_cannot_even_see_for_trigger_f(auth.getName(), upstream.getFullName(), job.getFullName()));
                } else {
                    LOGGER.log(Level.WARNING, "Running as {0} cannot even see {1} for trigger from {2} (but cannot tell {3} that)", new Object[] {auth.getName(), upstream, job, originalAuth.getName()});
                }
                return false;
            }
            // No need to check Item.BUILD on downstream, because the downstream project’s configurer has asked for this.
        } finally {
            SecurityContextHolder.setContext(orig);
        }
        Result result = upstreamBuild.getResult();
        return result != null && result.isBetterOrEqualTo(threshold);
    }

    @Override public void buildDependencyGraph(final AbstractProject downstream, DependencyGraph graph) {
        for (AbstractProject upstream : Items.fromNameList(downstream.getParent(), upstreamProjects, AbstractProject.class)) {
            graph.addDependency(new DependencyGraph.Dependency(upstream, downstream) {
                @Override public boolean shouldTriggerBuild(AbstractBuild upstreamBuild, TaskListener listener, List<Action> actions) {
                    return shouldTrigger(upstreamBuild, listener);
                }
            });
        }
    }

    @Override public void start(Job project, boolean newInstance) {
        super.start(project, newInstance);
        SecurityContext orig = ACL.impersonate(ACL.SYSTEM);
        try {
            for (Job upstream : Items.fromNameList(project.getParent(), upstreamProjects, Job.class)) {
                if (upstream instanceof AbstractProject && project instanceof AbstractProject) {
                    continue; // handled specially
                }
                synchronized (upstream2Trigger) {
                    Collection<ReverseBuildTrigger> triggers = upstream2Trigger.get(upstream);
                    if (triggers == null) {
                        triggers = new LinkedList<>();
                        upstream2Trigger.put(upstream, triggers);
                    }
                    triggers.remove(this);
                    triggers.add(this);
                }
            }
        } finally {
            SecurityContextHolder.setContext(orig);
        }
    }

    @Override public void stop() {
        super.stop();
        synchronized (upstream2Trigger) {
            for (Collection<ReverseBuildTrigger> triggers : upstream2Trigger.values()) {
                triggers.remove(this);
            }
        }
    }

    @Extension public static final class DescriptorImpl extends TriggerDescriptor {

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
            StringTokenizer tokens = new StringTokenizer(Util.fixNull(value),",");
            boolean hasProjects = false;
            while(tokens.hasMoreTokens()) {
                String projectName = tokens.nextToken().trim();
                if (StringUtils.isNotBlank(projectName)) {
                    Jenkins jenkins = Jenkins.getInstance();
                    if (jenkins == null) {
                        return FormValidation.ok();
                    }
                    Job item = jenkins.getItem(projectName, project, Job.class);
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
        @Override public void onCompleted(@Nonnull Run r, @Nonnull TaskListener listener) {
            Collection<ReverseBuildTrigger> triggers;
            synchronized (upstream2Trigger) {
                Collection<ReverseBuildTrigger> _triggers = upstream2Trigger.get(r.getParent());
                if (_triggers == null || _triggers.isEmpty()) {
                    return;
                }
                triggers = new ArrayList<>(_triggers);
            }
            for (final ReverseBuildTrigger trigger : triggers) {
                if (trigger.shouldTrigger(r, listener)) {
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

    @Extension public static class ItemListenerImpl extends ItemListener {
        @Override public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                return;
            }
            for (Job<?,?> p : jenkins.getAllItems(Job.class)) {
                ReverseBuildTrigger t = ParameterizedJobMixIn.getTrigger(p, ReverseBuildTrigger.class);
                if (t != null) {
                    String revised = Items.computeRelativeNamesAfterRenaming(oldFullName, newFullName, t.upstreamProjects, p.getParent());
                    if (!revised.equals(t.upstreamProjects)) {
                        t.upstreamProjects = revised;
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
