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

import hudson.ExtensionPoint;
import hudson.Extension;
import hudson.DescriptorExtensionList;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;


/**
 * {@link BuildStep}s that perform the actual build.
 *
 * <p>
 * To register a custom {@link Builder} from a plugin,
 * put {@link Extension} on your descriptor.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Builder extends BuildStepCompatibilityLayer implements Describable<Builder>, ExtensionPoint {
    

//
// these two methods need to remain to keep binary compatibility with plugins built with Hudson < 1.150
//
    /**
     * Default implementation that does nothing.
     */
    public boolean prebuild(Build build, BuildListener listener) {
        return true;
    }

    /**
     * Returns {@link BuildStepMonitor#NONE} by default, as {@link Builder}s normally don't depend
     * on its previous result.
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public Descriptor<Builder> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Returns all the registered {@link Builder} descriptors.
     */
    // for backward compatibility, the signature is not BuildStepDescriptor
    public static DescriptorExtensionList<Builder,Descriptor<Builder>> all() {
        return Jenkins.getInstance().<Builder,Descriptor<Builder>>getDescriptorList(Builder.class);
    }
}
