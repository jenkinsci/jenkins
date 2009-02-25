/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Tom Huybrechts
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

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@link NodeProperty} that allows users to specify different locations for {@link ToolInstallation}s.
 *
 * @since 1.286
 */
public class ToolLocationNodeProperty extends NodeProperty<Node> {

    private final List<ToolLocation> locations;

    @DataBoundConstructor
    public ToolLocationNodeProperty(List<ToolLocation> locations) {
        this.locations = locations;
    }

    public ToolLocationNodeProperty(ToolLocation... locations) {
        this(Arrays.asList(locations));
    }

    public List<ToolLocation> getLocations() {
        if (locations == null) return Collections.emptyList();
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

    public static String getToolHome(Node node, ToolInstallation installation) {
        ToolLocationNodeProperty property = node.getNodeProperties().get(ToolLocationNodeProperty.class);
        if (property != null) {
            return property.getHome(installation);
        }
        return null;
    }

    @Extension
    public static class DescriptorImpl extends NodePropertyDescriptor {

        public String getDisplayName() {
            return "Tool Locations";
        }

        public DescriptorExtensionList<ToolInstallation,ToolDescriptor<?>> getToolDescriptors() {
            return ToolInstallation.all();
        }

        public String getKey(ToolInstallation installation) {
            return installation.getDescriptor().getClass().getName() + "@" + installation.getName();
        }

        @Override
        public boolean isApplicable(Class<? extends Node> nodeType) {
            return nodeType != Hudson.class;
        }
    }

    public static final class ToolLocation {
        private final String type;
        private final String name;
        private final String home;
        private transient ToolDescriptor descriptor;

        public ToolLocation(ToolDescriptor type, String name, String home) {
            this.descriptor = type;
            this.type = type.getClass().getName();
            this.name = name;
            this.home = home;
        }
        
        @DataBoundConstructor
        public ToolLocation(String key, String home) {
            this.type =  key.substring(0, key.indexOf('@'));
            this.name = key.substring(key.indexOf('@') + 1);
            this.home = home;
        }

        public String getName() {
            return name;
        }

        public String getHome() {
            return home;
        }

        public ToolDescriptor getType() {
            if (descriptor == null) descriptor = (ToolDescriptor) Descriptor.find(type); 
            return descriptor;
        }

        public String getKey() {
            return type.getClass().getName() + "@" + name;
        }

    }
    

}
