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

import hudson.FilePath;
import hudson.Extension;

import java.io.File;
import java.io.IOException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;

/**
 * Free-style software project.
 * 
 * @author Kohsuke Kawaguchi
 */
public class FreeStyleProject extends Project<FreeStyleProject,FreeStyleBuild> implements TopLevelItem {
    /**
     * User-specified workspace directory, or null if it's up to Hudson.
     *
     * <p>
     * Normally a free-style project uses the workspace location assigned by its parent container,
     * but sometimes people have builds that have hard-coded paths (which can be only built in
     * certain locations. see http://www.nabble.com/Customize-Workspace-directory-tt17194310.html for
     * one such discussion.)
     *
     * <p>
     * This is not {@link File} because it may have to hold a path representation on another OS.
     *
     * @since 1.216
     */
    private String customWorkspace;

    public FreeStyleProject(Hudson parent, String name) {
        super(parent, name);
    }

    @Override
    protected Class<FreeStyleBuild> getBuildClass() {
        return FreeStyleBuild.class;
    }

    @Override
    public Hudson getParent() {
        return Hudson.getInstance();
    }

    public String getCustomWorkspace() {
        return customWorkspace;
    }

    @Override
    public FilePath getWorkspace() {
        Node node = getLastBuiltOn();
        if(node==null)  node = getParent();
        if(customWorkspace!=null)
            return node.createPath(customWorkspace);
        return node.getWorkspaceFor(this);
    }

    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        if(req.hasParameter("customWorkspace"))
            customWorkspace = req.getParameter("customWorkspace.directory");
        else
            customWorkspace = null;

        super.submit(req, rsp);
    }

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension(ordinal=1000)
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends AbstractProjectDescriptor {
        public String getDisplayName() {
            return Messages.FreeStyleProject_DisplayName();
        }

        public FreeStyleProject newInstance(String name) {
            return new FreeStyleProject(Hudson.getInstance(),name);
        }
    }
}
