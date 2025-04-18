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

package jenkins.model;

import static jakarta.servlet.http.HttpServletResponse.SC_CONFLICT;
import static jakarta.servlet.http.HttpServletResponse.SC_CREATED;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Util;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.model.Action;
import hudson.model.BuildAuthorizationToken;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.listeners.ItemListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.search.SearchIndexBuilder;
import hudson.triggers.Trigger;
import hudson.util.AlternativeUiTextProvider;
import hudson.views.BuildButtonColumn;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.model.lazy.LazyBuildMixIn;
import jenkins.triggers.SCMTriggerItem;
import jenkins.triggers.TriggeredItem;
import jenkins.util.TimeDuration;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.accmod.restrictions.ProtectedExternally;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Allows a {@link Job} to make use of {@link ParametersDefinitionProperty} and be scheduled in various ways.
 * Stateless so there is no need to keep an instance of it in a field.
 * Besides implementing {@link ParameterizedJob}, you should
 * <ul>
 * <li>override {@link Job#makeSearchIndex} to call {@link #extendSearchIndex}
 * <li>override {@link Job#performDelete} to call {@link ParameterizedJob#makeDisabled}
 * <li>override {@link Job#getIconColor} to call {@link ParameterizedJob#isDisabled}
 * <li>use {@code <p:config-disableBuild/>}
 * <li>use {@code <p:makeDisabled/>}
 * </ul>
 * @since 1.556
 */
@SuppressWarnings("unchecked") // AbstractItem.getParent does not correctly override; scheduleBuild2 inherently untypable
public abstract class ParameterizedJobMixIn<JobT extends Job<JobT, RunT> & ParameterizedJobMixIn.ParameterizedJob<JobT, RunT> & Queue.Task, RunT extends Run<JobT, RunT> & Queue.Executable> {

    protected abstract JobT asJob();

    /** @see BuildableItem#scheduleBuild() */
    @SuppressWarnings("deprecation")
    public final boolean scheduleBuild() {
        return scheduleBuild(asJob().getQuietPeriod(), new Cause.LegacyCodeCause());
    }

    /** @see BuildableItem#scheduleBuild(Cause) */
    public final boolean scheduleBuild(Cause c) {
        return scheduleBuild(asJob().getQuietPeriod(), c);
    }

    /** @see BuildableItem#scheduleBuild(int) */
    @SuppressWarnings("deprecation")
    public final boolean scheduleBuild(int quietPeriod) {
        return scheduleBuild(quietPeriod, new Cause.LegacyCodeCause());
    }

    /** @see BuildableItem#scheduleBuild(int, Cause) */
    public final boolean scheduleBuild(int quietPeriod, Cause c) {
        return scheduleBuild2(quietPeriod, c != null ? List.of(new CauseAction(c)) : Collections.emptyList()) != null;
    }

    /**
     * Standard implementation of {@link ParameterizedJob#scheduleBuild2}.
     */
    public final @CheckForNull QueueTaskFuture<RunT> scheduleBuild2(int quietPeriod, Action... actions) {
        Queue.Item i = scheduleBuild2(quietPeriod, Arrays.asList(actions));
        return i != null ? (QueueTaskFuture) i.getFuture() : null;
    }

    /**
     * Convenience method to schedule a build.
     * Useful for {@link Trigger} implementations, for example.
     * If you need to wait for the build to start (or finish), use {@link Queue.Item#getFuture}.
     * @param job a job which might be schedulable
     * @param quietPeriod seconds to wait before starting; use {@code -1} to use the job’s default settings
     * @param actions various actions to associate with the scheduling, such as {@link ParametersAction} or {@link CauseAction}
     * @return a newly created, or reused, queue item if the job could be scheduled;
     *      null if it was refused for some reason (e.g., some {@link Queue.QueueDecisionHandler} rejected it),
     *      or if {@code job} is not a {@link ParameterizedJob} or it is not {@link Job#isBuildable})
     * @since 1.621
     */
    public static @CheckForNull Queue.Item scheduleBuild2(final Job<?, ?> job, int quietPeriod, Action... actions) {
        if (!(job instanceof ParameterizedJob)) {
            return null;
        }
        return new ParameterizedJobMixIn() {
            @Override protected Job asJob() {
                return job;
            }
        }.scheduleBuild2(quietPeriod == -1 ? ((ParameterizedJob) job).getQuietPeriod() : quietPeriod, Arrays.asList(actions));
    }

    @CheckForNull Queue.Item scheduleBuild2(int quietPeriod, List<Action> actions) {
        if (!asJob().isBuildable())
            return null;

        List<Action> queueActions = new ArrayList<>(actions);
        if (isParameterized() && Util.filter(queueActions, ParametersAction.class).isEmpty()) {
            queueActions.add(new ParametersAction(getDefaultParametersValues()));
        }
        return Jenkins.get().getQueue().schedule2(asJob(), quietPeriod, queueActions).getItem();
    }

    private List<ParameterValue> getDefaultParametersValues() {
        ParametersDefinitionProperty paramDefProp = asJob().getProperty(ParametersDefinitionProperty.class);
        ArrayList<ParameterValue> defValues = new ArrayList<>();

        /*
         * This check is made ONLY if someone will call this method even if isParametrized() is false.
         */
        if (paramDefProp == null)
            return defValues;

        /* Scan for all parameter with an associated default values */
        for (ParameterDefinition paramDefinition : paramDefProp.getParameterDefinitions())
        {
           ParameterValue defaultValue  = paramDefinition.getDefaultParameterValue();

            if (defaultValue != null)
                defValues.add(defaultValue);
        }

        return defValues;
    }

    /**
     * Standard implementation of {@link ParameterizedJob#isParameterized}.
     */
    public final boolean isParameterized() {
        return asJob().getProperty(ParametersDefinitionProperty.class) != null;
    }

    /**
     * Standard implementation of {@link ParameterizedJob#doBuild}.
     */
    @SuppressWarnings("deprecation")
    public final void doBuild(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter TimeDuration delay) throws IOException, ServletException {
        if (delay == null) {
            delay = new TimeDuration(TimeUnit.MILLISECONDS.convert(asJob().getQuietPeriod(), TimeUnit.SECONDS));
        }

        if (!asJob().isBuildable()) {
            throw HttpResponses.error(SC_CONFLICT, new IOException(asJob().getFullName() + " is not buildable"));
        }

        // if a build is parameterized, let that take over
        ParametersDefinitionProperty pp = asJob().getProperty(ParametersDefinitionProperty.class);
        if (pp != null && !req.getMethod().equals("POST")) {
            // show the parameter entry form.
            req.getView(pp, "index.jelly").forward(req, rsp);
            return;
        }

        BuildAuthorizationToken.checkPermission(asJob(), asJob().getAuthToken(), req, rsp);

        if (pp != null) {
            pp._doBuild(req, rsp, delay);
            return;
        }


        Queue.Item item = Jenkins.get().getQueue().schedule2(asJob(), delay.getTimeInSeconds(), getBuildCause(asJob(), req)).getItem();
        if (item != null) {
            // TODO JENKINS-66105 use SC_SEE_OTHER if !ScheduleResult.created
            rsp.sendRedirect(SC_CREATED, req.getContextPath() + '/' + item.getUrl());
        } else {
            rsp.sendRedirect(".");
        }
    }

    /**
     * Standard implementation of {@link ParameterizedJob#doBuildWithParameters}.
     */
    @SuppressWarnings("deprecation")
    public final void doBuildWithParameters(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter TimeDuration delay) throws IOException, ServletException {
        BuildAuthorizationToken.checkPermission(asJob(), asJob().getAuthToken(), req, rsp);

        ParametersDefinitionProperty pp = asJob().getProperty(ParametersDefinitionProperty.class);
        if (!asJob().isBuildable()) {
            throw HttpResponses.error(SC_CONFLICT, new IOException(asJob().getFullName() + " is not buildable!"));
        }
        if (pp != null) {
            pp.buildWithParameters(req, rsp, delay);
        } else {
            throw new IllegalStateException("This build is not parameterized!");
        }
    }

    /**
     * Standard implementation of {@link ParameterizedJob#doCancelQueue}.
     */
    @RequirePOST
    public final void doCancelQueue(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        asJob().checkPermission(Item.CANCEL);
        Jenkins.get().getQueue().cancel(asJob());
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Use from a {@link Job#makeSearchIndex} override.
     * @param sib the super value
     * @return the value to return
     */
    public final SearchIndexBuilder extendSearchIndex(SearchIndexBuilder sib) {
        if (asJob().isBuildable() && asJob().hasPermission(Item.BUILD)) {
            sib.add("build", "build");
        }
        return sib;
    }

    /**
     * Computes the build cause, using RemoteCause or UserCause as appropriate.
     */
    @Restricted(NoExternalUse.class)
    public static CauseAction getBuildCause(ParameterizedJob job, StaplerRequest2 req) {
        Cause cause;
        @SuppressWarnings("deprecation")
        BuildAuthorizationToken authToken = job.getAuthToken();
        if (authToken != null && authToken.getToken() != null && req.getParameter("token") != null) {
            // Optional additional cause text when starting via token
            String causeText = req.getParameter("cause");
            cause = new Cause.RemoteCause(req.getRemoteAddr(), causeText);
        } else {
            cause = new Cause.UserIdCause();
        }
        return new CauseAction(cause);
    }

    /**
     * Allows customization of the human-readable display name to be rendered in the <i>Build Now</i> link.
     * @see #getBuildNowText
     * @since 1.624
     */
    public static final AlternativeUiTextProvider.Message<ParameterizedJob> BUILD_NOW_TEXT = new AlternativeUiTextProvider.Message<>();
    public static final AlternativeUiTextProvider.Message<ParameterizedJob> BUILD_WITH_PARAMETERS_TEXT = new AlternativeUiTextProvider.Message<>();

    /**
     * Suggested implementation of {@link ParameterizedJob#getBuildNowText}.
     */
    public final String getBuildNowText() {
        return isParameterized() ? AlternativeUiTextProvider.get(BUILD_WITH_PARAMETERS_TEXT, asJob(),
                AlternativeUiTextProvider.get(BUILD_NOW_TEXT, asJob(), Messages.ParameterizedJobMixIn_build_with_parameters()))
                : AlternativeUiTextProvider.get(BUILD_NOW_TEXT, asJob(), Messages.ParameterizedJobMixIn_build_now());
    }

    /**
     * Checks for the existence of a specific trigger on a job.
     * @param <T> a trigger type
     * @param job a job
     * @param clazz the type of the trigger
     * @return a configured trigger of the requested type, or null if there is none such, or {@code job} is not a {@link ParameterizedJob}
     * @since 1.621
     */
    public static @CheckForNull <T extends Trigger<?>> T getTrigger(Job<?, ?> job, Class<T> clazz) {
        if (!(job instanceof ParameterizedJob)) {
            return null;
        }
        for (Trigger<?> t : ((ParameterizedJob<?, ?>) job).getTriggers().values()) {
            if (clazz.isInstance(t)) {
                return clazz.cast(t);
            }
        }
        return null;
    }

    /**
     * Marker for job using this mixin, and default implementations of many methods.
     */
    public interface ParameterizedJob<JobT extends Job<JobT, RunT> & ParameterizedJobMixIn.ParameterizedJob<JobT, RunT> & Queue.Task, RunT extends Run<JobT, RunT> & Queue.Executable> extends BuildableItem, TriggeredItem {

        /**
         * Used for CLI binding.
         */
        @Restricted(DoNotUse.class)
        @SuppressWarnings("rawtypes")
        @CLIResolver
        static ParameterizedJob resolveForCLI(@Argument(required = true, metaVar = "NAME", usage = "Job name") String name) throws CmdLineException {
            ParameterizedJob item = Jenkins.get().getItemByFullName(name, ParameterizedJob.class);
            if (item == null) {
                ParameterizedJob project = Items.findNearest(ParameterizedJob.class, name, Jenkins.get());
                throw new CmdLineException(null, project == null ?
                        hudson.model.Messages.AbstractItem_NoSuchJobExistsWithoutSuggestion(name) :
                        hudson.model.Messages.AbstractItem_NoSuchJobExists(name, project.getFullName()));
            }
            return item;
        }

        /**
         * Creates a helper object.
         * (Would have been done entirely as an interface with default methods had this been designed for Java 8.)
         */
        default ParameterizedJobMixIn<JobT, RunT> getParameterizedJobMixIn() {
            return new ParameterizedJobMixIn<>() {
                @SuppressWarnings("unchecked") // untypable
                @Override protected JobT asJob() {
                    return (JobT) ParameterizedJob.this;
                }
            };
        }

        @SuppressWarnings("deprecation")
        @CheckForNull BuildAuthorizationToken getAuthToken();

        /**
         * Quiet period for the job.
         * @return by default, {@link Jenkins#getQuietPeriod}
         */
        default int getQuietPeriod() {
            return Jenkins.get().getQuietPeriod();
        }

        /**
         * Text to display for a build button.
         * Uses {@link #BUILD_NOW_TEXT}.
         * @see ParameterizedJobMixIn#getBuildNowText
         */
        default String getBuildNowText() {
            return getParameterizedJobMixIn().getBuildNowText();
        }

        @Override
        default boolean scheduleBuild(Cause c) {
            return getParameterizedJobMixIn().scheduleBuild(c);
        }

        @Override
        default boolean scheduleBuild(int quietPeriod, Cause c) {
            return getParameterizedJobMixIn().scheduleBuild(quietPeriod, c);
        }

        /**
         * Provides a standard implementation of {@link SCMTriggerItem#scheduleBuild2} to schedule a build with the ability to wait for its result.
         * That job method is often used during functional tests ({@code JenkinsRule.assertBuildStatusSuccess}).
         * @param quietPeriod seconds to wait before starting (normally 0)
         * @param actions various actions to associate with the scheduling, such as {@link ParametersAction} or {@link CauseAction}
         * @return a handle by which you may wait for the build to complete (or just start); or null if the build was not actually scheduled for some reason
         */
        @CheckForNull
        default QueueTaskFuture<RunT> scheduleBuild2(int quietPeriod, Action... actions) {
            return getParameterizedJobMixIn().scheduleBuild2(quietPeriod, actions);
        }

        /**
         * Schedules a new build command.
         * @see ParameterizedJobMixIn#doBuild
         */
        default void doBuild(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter TimeDuration delay) throws IOException, ServletException {
            getParameterizedJobMixIn().doBuild(req, rsp, delay);
        }

        /**
         * Supports build trigger with parameters via an HTTP GET or POST.
         * Currently only String parameters are supported.
         * @see ParameterizedJobMixIn#doBuildWithParameters
         */
        default void doBuildWithParameters(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter TimeDuration delay) throws IOException, ServletException {
            getParameterizedJobMixIn().doBuildWithParameters(req, rsp, delay);
        }

        /**
         * Cancels a scheduled build.
         * @see ParameterizedJobMixIn#doCancelQueue
         */
        @RequirePOST
        default void doCancelQueue(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
            getParameterizedJobMixIn().doCancelQueue(req, rsp);
        }

        /**
         * Schedules a new SCM polling command.
         */
        @SuppressWarnings("deprecation")
        default void doPolling(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
            if (!(this instanceof SCMTriggerItem)) {
                rsp.sendError(404);
                return;
            }
            BuildAuthorizationToken.checkPermission((Job) this, getAuthToken(), req, rsp);
            ((SCMTriggerItem) this).schedulePolling();
            rsp.sendRedirect(".");
        }

        /**
         * For use from {@link BuildButtonColumn}.
         * @see ParameterizedJobMixIn#isParameterized
         */
        default boolean isParameterized() {
            return getParameterizedJobMixIn().isParameterized();
        }

        default boolean isDisabled() {
            return false;
        }

        @Restricted(ProtectedExternally.class)
        default void setDisabled(boolean disabled) {
            throw new UnsupportedOperationException("must be implemented if supportsMakeDisabled is overridden");
        }

        /**
         * Specifies whether this project may be disabled by the user.
         * @return true if the GUI should allow {@link #doDisable} and the like
         */
        default boolean supportsMakeDisabled() {
            return false;
        }

        /**
         * Marks the build as disabled.
         * The method will ignore the disable command if {@link #supportsMakeDisabled()}
         * returns false. The enable command will be executed in any case.
         * @param b true - disable, false - enable
         */
        default void makeDisabled(boolean b) throws IOException {
            if (isDisabled() == b) {
                return; // noop
            }
            if (b && !supportsMakeDisabled()) {
                return; // do nothing if the disabling is unsupported
            }
            setDisabled(b);
            if (b) {
                Jenkins.get().getQueue().cancel(this);
            }
            save();
            ItemListener.fireOnUpdated(this);
        }

        @CLIMethod(name = "disable-job")
        @RequirePOST
        default HttpResponse doDisable() throws IOException {
            checkPermission(CONFIGURE);
            makeDisabled(true);
            return new HttpRedirect(".");
        }

        @CLIMethod(name = "enable-job")
        @RequirePOST
        default HttpResponse doEnable() throws IOException {
            checkPermission(CONFIGURE);
            makeDisabled(false);
            return new HttpRedirect(".");
        }

        @Override
        default RunT createExecutable() throws IOException {
            if (isDisabled()) {
                return null;
            }
            if (this instanceof LazyBuildMixIn.LazyLoadingJob) {
                return (RunT) ((LazyBuildMixIn.LazyLoadingJob) this).getLazyBuildMixIn().newBuild();
            }
            return null;
        }

        default boolean isBuildable() {
            return !isDisabled() && !((Job) this).isHoldOffBuildUntilSave();
        }

    }

}
