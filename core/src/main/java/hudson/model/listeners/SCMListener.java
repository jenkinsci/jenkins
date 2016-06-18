/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model.listeners;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;

/**
 * Receives notifications about SCM activities in Hudson.
 *
 * <p>
 * This is an abstract class so that methods added in the future won't break existing listeners.
 *
 * <p>
 * Once instanciated, use the {@link #register()} method to start receiving events. 
 *
 * @author Kohsuke Kawaguchi
 * @see jenkins.model.Jenkins#getSCMListeners()
 * @since 1.70
 */
public abstract class SCMListener implements ExtensionPoint {

    /**
     * Should be called immediately after {@link SCM#checkout(Run, Launcher, FilePath, TaskListener, File)} is called.
     * @param pollingBaseline information about what actually was checked out, if that is available, and this checkout is intended to be included in the build’s polling (if it does any at all)
     * @throws Exception if the checkout should be considered failed
     * @since 1.568
     */
    public void onCheckout(Run<?,?> build, SCM scm, FilePath workspace, TaskListener listener, @CheckForNull File changelogFile, @CheckForNull SCMRevisionState pollingBaseline) throws Exception {}

    /**
     * Called once the changelog is determined.
     *
     * <p>
     * During a build, Jenkins fetches the update of the workspace from SCM,
     * and determines the changelog (see {@link SCM#checkout}). Immediately
     * after that, a build will invoke this method on all registered
     * {@link SCMListener}s.
     *
     * <p>
     * If a build failed before we successfully determine changelog, this method
     * will not be invoked (for example, if "cvs update" failed.) OTOH, this method
     * is invoked before the actual build (like ant invocation) happens. 
     *
     * <p>
     * This is an opportunity for SCM-related plugins to act on changelog.
     * A typical usage includes parsing commit messages and do cross-referencing
     * between other systems. Implementations can also contribute {@link Action}
     * to {@link AbstractBuild} (by {@code build.getActions().add(...)} to
     * display additional data on build views.
     *
     * <p>
     * TODO: once we have cvsnews plugin, refer to its usage.
     *
     * @param build
     *      The build in progress, which just finished determining changelog.
     *      At this point this build is still in progress. Never null.
     * @param listener
     *      {@link BuildListener} for on-going build. This can be used to report
     *      any errors or the general logging of what's going on. This will show
     *      up in the "console output" of the build. Never null.
     * @param changelog
     *      Set of changes detected in this build. This is the same value
     *      returned from {@link AbstractBuild#getChangeSet()} but passed
     *      separately for convenience.
     *
     * @throws Exception
     *      If any exception is thrown from this method, it will be recorded
     *      and causes the build to fail. 
     * @since 1.568
     */
    public void onChangeLogParsed(Run<?,?> build, SCM scm, TaskListener listener, ChangeLogSet<?> changelog) throws Exception {
        if (build instanceof AbstractBuild && listener instanceof BuildListener && Util.isOverridden(SCMListener.class, getClass(), "onChangeLogParsed", AbstractBuild.class, BuildListener.class, ChangeLogSet.class)) {
            onChangeLogParsed((AbstractBuild) build, (BuildListener) listener, changelog);
        }
    }

    @Deprecated
    public void onChangeLogParsed(AbstractBuild<?,?> build, BuildListener listener, ChangeLogSet<?> changelog) throws Exception {
        if (Util.isOverridden(SCMListener.class, getClass(), "onChangeLogParsed", Run.class, SCM.class, TaskListener.class, ChangeLogSet.class)) {
            onChangeLogParsed((Run) build, build.getProject().getScm(), listener, changelog);
        }
    }

    /**
     * @since 1.568
     */
    @SuppressWarnings("deprecation")
    public static Collection<? extends SCMListener> all() {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null) { // TODO use !Functions.isExtensionsAvailable() once JENKINS-33377
            return Collections.emptySet();
        }
        List<SCMListener> r = new ArrayList<SCMListener>(j.getExtensionList(SCMListener.class));
        for (SCMListener l : j.getSCMListeners()) {
            r.add(l);
        }
        return r;
    }

    /** @deprecated Use {@link Extension} instead. */
    @Deprecated
    public final void register() {
        Jenkins.getInstance().getSCMListeners().add(this);
    }

    /** @deprecated Use {@link Extension} instead. */
    @Deprecated
    public final boolean unregister() {
        return Jenkins.getInstance().getSCMListeners().remove(this);
    }
}
