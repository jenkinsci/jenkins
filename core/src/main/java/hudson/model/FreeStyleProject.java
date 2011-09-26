/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, id:cactusman
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
package hudson.model;

import hudson.Extension;
import jenkins.model.Jenkins;
import hudson.model.queue.CauseOfBlockage;

/**
 * Free-style software project.
 * 
 * @author Kohsuke Kawaguchi
 */
public class FreeStyleProject extends Project<FreeStyleProject,FreeStyleBuild> implements TopLevelItem {

    /**
     * @deprecated as of 1.390
     */
    public FreeStyleProject(Jenkins parent, String name) {
        super(parent, name);
    }

    public FreeStyleProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    protected Class<FreeStyleBuild> getBuildClass() {
        return FreeStyleBuild.class;
    }

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension(ordinal=1000)
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    @Override
    public CauseOfBlockage canRunOnNode(Node node) {
        try {
            JDK jdk = this.getJDK();
            // jdk == null in case no JDK is specified
            if(null != jdk) {
                String jdkhome = jdk.forNode(node, TaskListener.NULL).getHome();
                // if JAVA_HOME is empty or null, the required JDK is not available on this node
                if("".equals(jdkhome))
                    return new CauseOfBlockage.BecauseNodeIsBusy(node);   // this node is reserved for tasks that are tied to it
            }
        } catch (Exception ex) {
            /**
             * FIXME: how should we react?
             * for now we fall back to the historical behavior
             */ 
            return null;
        }

        return null;
    }

    public static final class DescriptorImpl extends AbstractProjectDescriptor {
        public String getDisplayName() {
            return Messages.FreeStyleProject_DisplayName();
        }

        public FreeStyleProject newInstance(ItemGroup parent, String name) {
            return new FreeStyleProject(parent,name);
        }
    }
}
