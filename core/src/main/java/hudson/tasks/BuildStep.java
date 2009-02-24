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

import hudson.Launcher;
import hudson.Extension;
import hudson.util.DescriptorList;
import hudson.maven.RedeployPublisher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.test.AggregatedTestResultPublisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
 * @author Kohsuke Kawaguchi
 */
public interface BuildStep {

    /**
     * Runs before the build begins.
     *
     * @return
     *      true if the build can continue, false if there was an error
     *      and the build needs to be aborted.
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
     * @return
     *      true if the build can continue, false if there was an error
     *      and the build needs to be aborted.
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
     * Returns an action object if this {@link BuildStep} has an action
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
     *      null if there's no action to be contributed.
     */
    Action getProjectAction(AbstractProject<?,?> project);

    /**
     * List of all installed builders.
     *
     * Builders are invoked to perform the build itself.
     *
     * @deprecated as of 1.286.
     *      Use {@link Builder#all()} for read access, and use
     *      {@link Extension} for registration.
     */
    public static final List<Descriptor<Builder>> BUILDERS = new DescriptorList<Builder>(Builder.class);

    /**
     * List of all installed publishers.
     *
     * Publishers are invoked after the build is completed, normally to perform
     * some post-actions on build results, such as sending notifications, collecting
     * results, etc.
     *
     * @see PublisherList#addNotifier(Descriptor)
     * @see PublisherList#addRecorder(Descriptor) 
     */
    public static final PublisherList PUBLISHERS = new PublisherList(Descriptor.toList(
        ArtifactArchiver.DESCRIPTOR,
        Fingerprinter.DESCRIPTOR,
        JavadocArchiver.DESCRIPTOR,
        JUnitResultArchiver.DescriptorImpl.DESCRIPTOR,
        AggregatedTestResultPublisher.DescriptorImpl.INSTANCE,
        BuildTrigger.DESCRIPTOR,
        RedeployPublisher.DESCRIPTOR,
        Mailer.DESCRIPTOR
    ));

    /**
     * List of publisher descriptor.
     */
    public static final class PublisherList extends ArrayList<Descriptor<Publisher>> {
        public PublisherList(Collection<? extends Descriptor<Publisher>> c) {
            super(c);
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
            add(d);
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
            int idx = super.indexOf(Mailer.DESCRIPTOR);
            add(idx,d);
        }

        @Override
        public boolean add(Descriptor<Publisher> d) {
            return !contains(d) && super.add(d);
        }

        @Override
        public void add(int index, Descriptor<Publisher> d) {
            if(!contains(d)) super.add(index, d);
        }
    }
}
