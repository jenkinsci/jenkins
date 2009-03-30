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

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Project;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.AbstractBuild;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * {@link BuildStep}s that run after the build is completed.
 *
 * <p>
 * To register a custom {@link Publisher} from a plugin,
 * put {@link Extension} on your descriptor implementation.
 *
 * <p>
 * Starting 1.178, publishers are exposed to all kinds of different
 * project type, not just the freestyle project type (in particular,
 * the native maven2 job type.) This is convenient default for
 * {@link Publisher}s in particular initially, but we encourage advanced
 * plugins to consider writing MavenReporter, as it offers the
 * potential of reducing the amount of configuration needed to run the plugin.
 *
 * For those plugins that don't want {@link Publisher} to show up in
 * different job type, use {@link BuildStepDescriptor} for the base type
 * of your descriptor to control which job type it supports.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Publisher extends BuildStepCompatibilityLayer implements BuildStep, Describable<Publisher> {
    /**
     * @deprecated
     *      Don't extend from {@link Publisher} directly. Instead, choose {@link Recorder} or {@link Notifier}
     *      as your base class.
     */
    protected Publisher() {
    }

    //
// these two methods need to remain to keep binary compatibility with plugins built with Hudson < 1.150
//
    /**
     * Default implementation that does nothing.
     */
    @Deprecated
    @Override
    public boolean prebuild(Build build, BuildListener listener) {
        return true;
    }

    /**
     * Default implementation that does nothing.
     */
    @Deprecated
    @Override
    public Action getProjectAction(Project project) {
        return null;
    }

    /**
     * Returne true if this {@link Publisher} needs to run after the build result is
     * fully finalized.
     *
     * <p>
     * The execution of normal {@link Publisher}s are considered within a part
     * of the build. This allows publishers to mark the build as a failure, or
     * to include their execution time in the total build time.
     *
     * <p>
     * So normally, that is the preferrable behavior, but in a few cases
     * this is problematic. One of such cases is when a publisher needs to
     * trigger other builds, whcih in turn need to see this build as a
     * completed build. Those plugins that need to do this can return true
     * from this method, so that the {@link #perform(AbstractBuild, Launcher, BuildListener)} 
     * method is called after the build is marked as completed.
     *
     * <p>
     * When {@link Publisher} behaves this way, note that they can no longer
     * change the build status anymore.
     *
     * @author Kohsuke Kawaguchi
     * @since 1.153
     */
    public boolean needsToRunAfterFinalized() {
        return false;
    }

    public Descriptor<Publisher> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    /**
     * {@link Publisher} has a special sort semantics that requires a subtype.
     *
     * @see DescriptorExtensionList#create(Hudson, Class) 
     */
    public static final class DescriptorExtensionListImpl extends DescriptorExtensionList<Publisher,Descriptor<Publisher>>
            implements Comparator<Descriptor<Publisher>> {
        public DescriptorExtensionListImpl(Hudson hudson) {
            super(hudson,Publisher.class);
        }

        @Override
        protected List<Descriptor<Publisher>> sort(List<Descriptor<Publisher>> r) {
            List<Descriptor<Publisher>> copy = new ArrayList<Descriptor<Publisher>>(r);
            Collections.sort(copy,this);
            return copy;
        }

        public int compare(Descriptor<Publisher> lhs, Descriptor<Publisher> rhs) {
            return classify(lhs)-classify(rhs);
        }

        /**
         * If recorder, return 0, if unknown return 1, if notifier returns 2.
         * This is used as a sort key.
         */
        private int classify(Descriptor<Publisher> d) {
            if(Recorder.class.isAssignableFrom(d.clazz))    return 0;
            if(Notifier.class.isAssignableFrom(d.clazz))    return 2;

            // for compatibility, if the descriptor is manually registered in a specific way, detect that.
            Class<? extends Publisher> kind = PublisherList.KIND.get(d);
            if(kind==Recorder.class)    return 0;
            if(kind==Notifier.class)    return 2;

            return 1;
        }
    }

    /**
     * Returns all the registered {@link Publisher} descriptors.
     */
    // for backward compatibility, the signature is not BuildStepDescriptor
    public static DescriptorExtensionList<Publisher,Descriptor<Publisher>> all() {
        return Hudson.getInstance().getDescriptorList(Publisher.class);
    }
}
