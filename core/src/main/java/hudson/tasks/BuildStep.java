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
package hudson.tasks;

import hudson.AbortException;
import hudson.Launcher;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.DescriptorList;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.model.CheckPoint;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.security.Permission;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.WeakHashMap;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticator;
import org.acegisecurity.Authentication;

import javax.annotation.Nonnull;

/**
 * One step of the whole build process.
 *
 * <h2>Persistence</h2>
 * <p>
 * These objects are persisted as a part of {@link Project} by XStream.
 * The save operation happens without any notice, and the restore operation
 * happens without calling the constructor, just like Java serialization.
 *
 * <p>
 * So generally speaking, derived classes should use instance variables
 * only for keeping configuration. You can still store objects you use
 * for processing, like a parser of some sort, but they need to be marked
 * as <tt>transient</tt>, and the code needs to be aware that they might
 * be null (which is the case when you access the field for the first time
 * the object is restored.)
 *
 * <h2>Lifecycle</h2>
 * <p>
 * Build steps are instantiated when the user saves the job configuration, and sticks
 * around in memory until the job configuration is overwritten.
 *
 * @author Kohsuke Kawaguchi
 */
public interface BuildStep {

    /**
     * Runs before the build begins.
     *
     * @return
     *      true if the build can continue, false if there was an error
     *      and the build needs to be aborted.
     *      <p>
     *      Using the return value to indicate success/failure should
     *      be considered deprecated, and implementations are encouraged
     *      to throw {@link AbortException} to indicate a failure.
     */
    boolean prebuild( AbstractBuild<?,?> build, BuildListener listener );

    /**
     * Runs the step over the given build and reports the progress to the listener.
     *
     * <p>
     * A plugin can contribute the action object to {@link Build#getActions()}
     * so that a 'report' becomes a part of the persisted data of {@link Build}.
     * This is how JUnit plugin attaches the test report to a build page, for example.
     *
     * <p>When this build step needs to make (direct or indirect) permission checks to {@link ACL}
     * (for example, to locate other projects by name, build them, or access their artifacts)
     * then it must be run under a specific {@link Authentication}.
     * In such a case, the implementation should check whether {@link Jenkins#getAuthentication} is {@link ACL#SYSTEM},
     * and if so, replace it for the duration of this step with {@link Jenkins#ANONYMOUS}.
     * (Either using {@link ACL#impersonate}, or by making explicit calls to {@link ACL#hasPermission(Authentication, Permission)}.)
     * This would typically happen when no {@link QueueItemAuthenticator} was available, configured, and active.
     *
     * @return
     *      true if the build can continue, false if there was an error
     *      and the build needs to be aborted.
     *      <p>
     *      Using the return value to indicate success/failure should
     *      be considered deprecated, and implementations are encouraged
     *      to throw {@link AbortException} to indicate a failure.
     *
     * @throws InterruptedException
     *      If the build is interrupted by the user (in an attempt to abort the build.)
     *      Normally the {@link BuildStep} implementations may simply forward the exception
     *      it got from its lower-level functions.
     * @throws IOException
     *      If the implementation wants to abort the processing when an {@link IOException}
     *      happens, it can simply propagate the exception to the caller. This will cause
     *      the build to fail, with the default error message.
     *      Implementations are encouraged to catch {@link IOException} on its own to
     *      provide a better error message, if it can do so, so that users have better
     *      understanding on why it failed.
     */
    boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException;

    /**
     * @deprecated as of 1.341.
     *      Use {@link #getProjectActions(AbstractProject)} instead.
     */
    @Deprecated
    Action getProjectAction(AbstractProject<?,?> project);

    /**
     * Returns action objects if this {@link BuildStep} has actions
     * to contribute to a {@link Project}.
     *
     * <p>
     * {@link Project} calls this method for every {@link BuildStep} that
     * it owns when the rendering is requested.
     *
     * <p>
     * This action can have optional <tt>jobMain.jelly</tt> view, which will be
     * aggregated into the main panel of the job top page. The jelly file
     * should have an &lt;h2> tag that shows the section title, followed by some
     * block elements to render the details of the section.
     *
     * @param project
     *      {@link Project} that owns this build step,
     *      since {@link BuildStep} object doesn't usually have this "parent" pointer.
     *
     * @return
     *      can be empty but never null.
     */
    @Nonnull
    Collection<? extends Action> getProjectActions(AbstractProject<?,?> project);


    /**
     * Declares the scope of the synchronization monitor this {@link BuildStep} expects from outside.
     *
     * <p>
     * This method is introduced for preserving compatibility with plugins written for earlier versions of Hudson,
     * which never run multiple builds of the same job in parallel. Such plugins often assume that the outcome
     * of the previous build is completely available, which is no longer true when we do concurrent builds.
     *
     * <p>
     * To minimize the necessary code change for such plugins, {@link BuildStep} implementations can request
     * Hudson to externally perform synchronization before executing them. This behavior is as follows:
     *
     * <dl>
     * <dt>{@link BuildStepMonitor#BUILD}
     * <dd>
     * This {@link BuildStep} is only executed after the previous build is fully
     * completed (thus fully restoring the earlier semantics of one build at a time.)
     *
     * <dt>{@link BuildStepMonitor#STEP}
     * <dd>
     * This {@link BuildStep} is only executed after the same step in the previous build is completed.
     * For build steps that use a weaker assumption and only rely on the output from the same build step of
     * the early builds, this improves the concurrency.
     *
     * <dt>{@link BuildStepMonitor#NONE}
     * <dd>
     * No external synchronization is performed on this build step. This is the most efficient, and thus
     * <b>the recommended value for newer plugins</b>. Wherever necessary, you can directly use {@link CheckPoint}s
     * to perform necessary synchronizations.
     * </dl>
     *
     * <h2>Migrating Older Implementation</h2>
     * <p>
     * If you are migrating {@link BuildStep} implementations written for earlier versions of Hudson,
     * here's what you can do:
     *
     * <ul>
     * <li>
     * Just return {@link BuildStepMonitor#BUILD} to demand the backward compatible behavior from Hudson,
     * and make no other changes to the code. This will prevent users from reaping the benefits of concurrent
     * builds, but at least your plugin will work correctly, and therefore this is a good easy first step.
     * <li>
     * If your build step doesn't use anything from a previous build (for example, if you don't even call
     * {@link Run#getPreviousBuild()}), then you can return {@link BuildStepMonitor#NONE} without making further
     * code changes and you are done with migration.
     * <li>
     * If your build step only depends on {@link Action}s that you added in the previous build by yourself,
     * then you only need {@link BuildStepMonitor#STEP} scope synchronization. Return it from this method
     * ,and you are done with migration without any further code changes.
     * <li>
     * If your build step makes more complex assumptions, return {@link BuildStepMonitor#NONE} and use
     * {@link CheckPoint}s directly in your code. The general idea is to call {@link CheckPoint#block()} before
     * you try to access the state from the previous build.
     * </ul>
     *
     * <h2>Note to caller</h2>
     * <p>
     * For plugins written against earlier versions of Hudson, calling this method results in
     * {@link AbstractMethodError}. 
     *
     * @since 1.319
     */
    BuildStepMonitor getRequiredMonitorService();

    /**
     * List of all installed builders.
     *
     * Builders are invoked to perform the build itself.
     *
     * @deprecated as of 1.286.
     *      Use {@link Builder#all()} for read access, and use
     *      {@link Extension} for registration.
     */
    @Deprecated
    List<Descriptor<Builder>> BUILDERS = new DescriptorList<Builder>(Builder.class);

    /**
     * List of all installed publishers.
     *
     * Publishers are invoked after the build is completed, normally to perform
     * some post-actions on build results, such as sending notifications, collecting
     * results, etc.
     *
     * @see PublisherList#addNotifier(Descriptor)
     * @see PublisherList#addRecorder(Descriptor)
     *
     * @deprecated as of 1.286.
     *      Use {@link Publisher#all()} for read access, and use
     *      {@link Extension} for registration.
     */
    @Deprecated
    PublisherList PUBLISHERS = new PublisherList();

    /**
     * List of publisher descriptor.
     */
    final class PublisherList extends AbstractList<Descriptor<Publisher>> {
        /**
         * {@link Descriptor}s are actually stored in here.
         * Since {@link PublisherList} lives longer than {@link jenkins.model.Jenkins} we cannot directly use {@link ExtensionList}.
         */
        private final DescriptorList<Publisher> core = new DescriptorList<Publisher>(Publisher.class);

        /**
         * For descriptors that are manually registered, remember what kind it was since
         * older plugins don't extend from neither {@link Recorder} nor {@link Notifier}.
         */
        /*package*/ static final WeakHashMap<Descriptor<Publisher>,Class<? extends Publisher>/*either Recorder.class or Notifier.class*/>
                KIND = new WeakHashMap<Descriptor<Publisher>, Class<? extends Publisher>>();

        private PublisherList() {
        }

        /**
         * Adds a new publisher descriptor, which (generally speaking)
         * shouldn't alter the build result, but just report the build result
         * by some means, such as e-mail, IRC, etc.
         *
         * <p>
         * This method adds the descriptor after all the "recorders".
         *
         * @see #addRecorder(Descriptor)
         */
        public void addNotifier( Descriptor<Publisher> d ) {
            KIND.put(d,Notifier.class);
            core.add(d);
        }
        
        /**
         * Adds a new publisher descriptor, which (generally speaking)
         * alter the build result based on some artifacts of the build.
         *
         * <p>
         * This method adds the descriptor before all the "notifiers".
         *
         * @see #addNotifier(Descriptor) 
         */
        public void addRecorder( Descriptor<Publisher> d ) {
            KIND.put(d,Recorder.class);
            core.add(d);
        }

        @Override
        public boolean add(Descriptor<Publisher> d) {
            return !contains(d) && core.add(d);
        }

        @Override
        public void add(int index, Descriptor<Publisher> d) {
            if(!contains(d)) core.add(d);
        }

        public Descriptor<Publisher> get(int index) {
            return core.get(index);
        }

        public int size() {
            return core.size();
        }

        @Override
        public Iterator<Descriptor<Publisher>> iterator() {
            return core.iterator();
        }

        @Override
        public boolean remove(Object o) {
            return core.remove(o);
        }
    }
}
