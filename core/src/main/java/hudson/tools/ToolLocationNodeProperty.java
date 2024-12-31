/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Tom Huybrechts, Seiji Sogabe
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

package hudson.tools;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.agents.NodeProperty;
import hudson.agents.NodePropertyDescriptor;
import hudson.agents.NodeSpecific;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link NodeProperty} that allows users to specify different locations for {@link ToolInstallation}s.
 *
 * @since 1.286
 */
public class ToolLocationNodeProperty extends NodeProperty<Node> {

    /**
     * Override locations. Never null.
     */
    private final List<ToolLocation> locations;

    @DataBoundConstructor
    public ToolLocationNodeProperty(List<ToolLocation> locations) {
        if (locations == null) {
            locations = new ArrayList<>();
        }
        this.locations = locations;
    }

    public ToolLocationNodeProperty(ToolLocation... locations) {
        this(Arrays.asList(locations));
    }

    public List<ToolLocation> getLocations() {
        return Collections.unmodifiableList(locations);
    }

    public String getHome(ToolInstallation installation) {
        for (ToolLocation location : locations) {
            if (location.getName().equals(installation.getName()) && location.getType() == installation.getDescriptor()) {
                return location.getHome();
            }
        }
        return null;
    }

    /**
     * Checks if the location of the tool is overridden for the given node, and if so,
     * return the node-specific home directory. Otherwise return {@code installation.getHome()}
     *
     * <p>
     * This is the core logic behind {@link NodeSpecific#forNode(Node, TaskListener)} for {@link ToolInstallation}.
     *
     * @return
     *      never null.
     * @deprecated since 2009-04-09.
     *      Use {@link ToolInstallation#translateFor(Node,TaskListener)}
     */
    @Deprecated
    public static String getToolHome(Node node, ToolInstallation installation, TaskListener log) throws IOException, InterruptedException {
        String result = null;

        // node-specific configuration takes precedence
        ToolLocationNodeProperty property = node.getNodeProperties().get(ToolLocationNodeProperty.class);
        if (property != null) {
            result = property.getHome(installation);
        }
        if (result != null) {
            return result;
        }

        // consult translators
        for (ToolLocationTranslator t : ToolLocationTranslator.all()) {
            result = t.getToolHome(node, installation, log);
            if (result != null) {
                return result;
            }
        }

        // fall back is no-op
        return installation.getHome();
    }

    @Extension @Symbol("toolLocation")
    public static class DescriptorImpl extends NodePropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ToolLocationNodeProperty_displayName();
        }

        public DescriptorExtensionList<ToolInstallation, ToolDescriptor<?>> getToolDescriptors() {
            return ToolInstallation.all();
        }

        public String getKey(ToolInstallation installation) {
            return installation.getDescriptor().getClass().getName() + "@" + installation.getName();
        }

        @Override
        public boolean isApplicable(Class<? extends Node> nodeType) {
            return nodeType != Jenkins.class;
        }
    }

    public static final class ToolLocation {

        private final String type;

        private final String name;

        private final String home;

        private transient volatile ToolDescriptor descriptor;

        public ToolLocation(ToolDescriptor type, String name, String home) {
            this.descriptor = type;
            this.type = type.getClass().getName();
            this.name = name;
            this.home = home;
        }

        @DataBoundConstructor
        public ToolLocation(String key, String home) {
            this.type = key.substring(0, key.indexOf('@'));
            this.name = key.substring(key.indexOf('@') + 1);
            this.home = home;
        }

        public String getName() {
            return name;
        }

        public String getHome() {
            return home;
        }

        @SuppressWarnings("deprecation") // TODO this was mistakenly made to be the ToolDescriptor class name, rather than .id as you would expect; now baked into serial form
        public ToolDescriptor getType() {
            if (descriptor == null) {
                descriptor = (ToolDescriptor) Descriptor.find(type);
            }
            return descriptor;
        }

        public String getKey() {
            return type + "@" + name;
        }
    }
}
