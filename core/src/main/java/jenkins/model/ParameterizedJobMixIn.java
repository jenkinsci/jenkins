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

import hudson.Util;
import hudson.model.Action;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import hudson.search.SearchIndexBuilder;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.views.BuildButtonColumn;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import jenkins.util.TimeDuration;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Allows a {@link Job} to make use of {@link ParametersDefinitionProperty} and be scheduled in various ways.
 * Stateless so there is no need to keep an instance of it in a field.
 * @since 1.556
 */
@SuppressWarnings("unchecked") // AbstractItem.getParent does not correctly override; scheduleBuild2 inherently untypable
public abstract class ParameterizedJobMixIn<JobT extends Job<JobT, RunT> & ParameterizedJobMixIn.ParameterizedJob & Queue.Task, RunT extends Run<JobT, RunT> & Queue.Executable> {
    
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
        return scheduleBuild2(quietPeriod, c != null ? Collections.<Action>singletonList(new CauseAction(c)) : Collections.<Action>emptyList()) != null;
    }

    /**
     * Convenience method to schedule a build with the ability to wait for its result.
     * Often used during functional tests ({@code JenkinsRule.assertBuildStatusSuccess}).
     * @param quietPeriod seconds to wait before starting (normally 0)
     * @param actions various actions to associate with the scheduling, such as {@link ParametersAction} or {@link CauseAction}
     * @return a handle by which you may wait for the build to complete (or just start); or null if the build was not actually scheduled for some reason
     */
    public final @CheckForNull QueueTaskFuture<RunT> scheduleBuild2(int quietPeriod, Action... actions) {
        return scheduleBuild2(quietPeriod, Arrays.asList(actions));
    }

    private @CheckForNull QueueTaskFuture<RunT> scheduleBuild2(int quietPeriod, List<Action> actions) {
        if (!asJob().isBuildable())
            return null;

        List<Action> queueActions = new ArrayList<Action>(actions);
        if (isParameterized() && Util.filter(queueActions, ParametersAction.class).isEmpty()) {
            queueActions.add(new ParametersAction(getDefaultParametersValues()));
        }
        Queue.Item i = Jenkins.getInstance().getQueue().schedule2(asJob(), quietPeriod, queueActions).getItem();
        return i != null ? (QueueTaskFuture) i.getFuture() : null;
    }

    private List<ParameterValue> getDefaultParametersValues() {
        ParametersDefinitionProperty paramDefProp = asJob().getProperty(ParametersDefinitionProperty.class);
        ArrayList<ParameterValue> defValues = new ArrayList<ParameterValue>();

        /*
         * This check is made ONLY if someone will call this method even if isParametrized() is false.
         */
        if(paramDefProp == null)
            return defValues;

        /* Scan for all parameter with an associated default values */
        for(ParameterDefinition paramDefinition : paramDefProp.getParameterDefinitions())
        {
           ParameterValue defaultValue  = paramDefinition.getDefaultParameterValue();

            if(defaultValue != null)
                defValues.add(defaultValue);
        }

        return defValues;
    }

    /**
     * A job should define a method of the same signature for use from {@link BuildButtonColumn}.
     */
    public final boolean isParameterized() {
        return asJob().getProperty(ParametersDefinitionProperty.class) != null;
    }

    /**
     * Schedules a new build command.
     * Create a method on your job with the same signature and delegate to this.
     */
    @SuppressWarnings("deprecation")
    public final void doBuild(StaplerRequest req, StaplerResponse rsp, @QueryParameter TimeDuration delay) throws IOException, ServletException {
        if (delay == null) {
            delay = new TimeDuration(asJob().getQuietPeriod());
        }

        // if a build is parameterized, let that take over
        ParametersDefinitionProperty pp = asJob().getProperty(ParametersDefinitionProperty.class);
        if (pp != null && !req.getMethod().equals("POST")) {
            // show the parameter entry form.
            req.getView(pp, "index.jelly").forward(req, rsp);
            return;
        }

        hudson.model.BuildAuthorizationToken.checkPermission(asJob(), asJob().getAuthToken(), req, rsp);

        if (pp != null) {
            pp._doBuild(req, rsp, delay);
            return;
        }

        if (!asJob().isBuildable()) {
            throw HttpResponses.error(SC_INTERNAL_SERVER_ERROR, new IOException(asJob().getFullName() + " is not buildable"));
        }

        Queue.Item item = Jenkins.getInstance().getQueue().schedule2(asJob(), delay.getTime(), getBuildCause(asJob(), req)).getItem();
        if (item != null) {
            rsp.sendRedirect(SC_CREATED, req.getContextPath() + '/' + item.getUrl());
        } else {
            rsp.sendRedirect(".");
        }
    }

    /**
     * Supports build trigger with parameters via an HTTP GET or POST.
     * Currently only String parameters are supported.
     * Create a method on your job with the same signature and delegate to this.
     */
    @SuppressWarnings("deprecation")
    public final void doBuildWithParameters(StaplerRequest req, StaplerResponse rsp, @QueryParameter TimeDuration delay) throws IOException, ServletException {
        hudson.model.BuildAuthorizationToken.checkPermission(asJob(), asJob().getAuthToken(), req, rsp);

        ParametersDefinitionProperty pp = asJob().getProperty(ParametersDefinitionProperty.class);
        if (pp != null) {
            pp.buildWithParameters(req, rsp, delay);
        } else {
            throw new IllegalStateException("This build is not parameterized!");
        }
    }

    /**
     * Cancels a scheduled build.
     * Create a method on your job marked {@link RequirePOST} but with the same signature and delegate to this.
     */
    @RequirePOST
    public final void doCancelQueue( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        asJob().checkPermission(Item.CANCEL);
        Jenkins.getInstance().getQueue().cancel(asJob());
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
    public static final CauseAction getBuildCause(ParameterizedJob job, StaplerRequest req) {
        Cause cause;
        @SuppressWarnings("deprecation")
        hudson.model.BuildAuthorizationToken authToken = job.getAuthToken();
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
     * Suggested implementation of {@link ParameterizedJob#getBuildNowText}.
     */
    public final String getBuildNowText() {
        // TODO is it worthwhile to define a replacement for AbstractProject.BUILD_NOW_TEXT?
        // TODO move these messages (& translations) to this package
        return isParameterized() ? hudson.model.Messages.AbstractProject_build_with_parameters() : hudson.model.Messages.AbstractProject_BuildNow();
    }

    /**
     * Marker for job using this mixin.
     */
    public interface ParameterizedJob extends hudson.model.Queue.Task, hudson.model.Item {

        @SuppressWarnings("deprecation")
        @CheckForNull hudson.model.BuildAuthorizationToken getAuthToken();

        int getQuietPeriod();

        String getBuildNowText();

        /**
         * Gets currently configured triggers.
         * You may use {@code <p:config-trigger/>} to configure them.
         * @return a map from trigger kind to instance
         */
        Map<TriggerDescriptor,Trigger<?>> getTriggers();

    }

}
