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

package hudson.agents;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Util;
import hudson.model.ComputerSet;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.model.Node;
import hudson.model.Agent;
import hudson.util.DescriptorList;
import hudson.util.FormValidation;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * {@link Descriptor} for {@link Agent}.
 *
 * <h2>Views</h2>
 * <p>
 * This object needs to have {@code newInstanceDetail.jelly} view, which shows up in
 * {@code http://server/hudson/computers/new} page as an explanation of this job type.
 *
 * <h2>Other Implementation Notes</h2>
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class NodeDescriptor extends Descriptor<Node> {
    protected NodeDescriptor(Class<? extends Node> clazz) {
        super(clazz);
    }

    protected NodeDescriptor() {
    }

    /**
     * Can the administrator create this type of nodes from UI?
     *
     * Return false if it only makes sense for programs to create it, not through the "new node" UI.
     */
    public boolean isInstantiable() {
        return true;
    }

    public final String newInstanceDetailPage() {
        return '/' + clazz.getName().replace('.', '/').replace('$', '/') + "/newInstanceDetail.jelly";
    }

    /**
     * Handles the form submission from the "/computer/new" page, which is the first form for creating a new node.
     * By default, it shows the configuration page for entering details, but subtypes can override this differently.
     *
     * @param name
     *      Name of the new node.
     */
    public void handleNewNodePage(ComputerSet computerSet, String name, StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        computerSet.checkName(name);
        req.setAttribute("descriptor", this);
        req.getView(computerSet, "_new.jelly").forward(req, rsp);
    }

    @Override
    public String getConfigPage() {
        return getViewPage(clazz, "configure-entries.jelly");
    }

    public FormValidation doCheckName(@QueryParameter String value) {
        String name = Util.fixEmptyAndTrim(value);
        if (name == null)
            return FormValidation.error(Messages.NodeDescriptor_CheckName_Mandatory());
        try {
            Jenkins.checkGoodName(name);
        } catch (Failure f) {
            return FormValidation.error(f.getMessage());
        }
        return FormValidation.ok();
    }

    /**
     * Returns all the registered {@link NodeDescriptor} descriptors.
     */
    public static DescriptorExtensionList<Node, NodeDescriptor> all() {
        return Jenkins.get().getDescriptorList(Node.class);
    }

    /**
     * All the registered instances.
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access, and {@link Extension} for registration.
     */
    @Deprecated
    public static final DescriptorList<Node> ALL = new DescriptorList<>(Node.class);

    public static List<NodeDescriptor> allInstantiable() {
        List<NodeDescriptor> r = new ArrayList<>();
        for (NodeDescriptor d : all())
            if (d.isInstantiable())
                r.add(d);
        return r;
    }
}
