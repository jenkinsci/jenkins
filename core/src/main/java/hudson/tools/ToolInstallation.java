/*
 * The MIT License
 *
 * Copyright (c) 2004-${date.year}, Sun Microsystems, Inc., Tom Huybrechts
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
import hudson.ExtensionPoint;
import hudson.EnvVars;
import hudson.model.Describable;
import hudson.model.EnvironmentSpecific;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.slaves.NodeSpecific;

import java.io.Serializable;

/**
 * Formalization of a tool installed in nodes used for builds
 * (examples include things like JDKs, Ants, Mavens, and Groovys)
 *
 * <p>
 * You can define such a concept in your plugin entirely on your own, without extending from
 * this class, but choosing this class as a base class has several benefits:
 *
 * <ul>
 * <li>Hudson allows admins to specify different locations for tools on some slaves.
 *     For example, JDK on the master might be on /usr/local/java but on a Windows slave
 *     it could be at c:\Program Files\Java
 * <li>Hudson can verify the existence of tools and provide warnings and diagnostics for
 *     admins. (TBD)
 * <li>Hudson can perform automatic installations for users. (TBD)
 * </ul>
 *
 * <p>
 * Implementations of this class are strongly encouraged to also implement {@link NodeSpecific}
 * (by using {@link ToolLocationNodeProperty#getToolHome(Node, ToolInstallation)}) and
 * {@link EnvironmentSpecific} (by using {@link EnvVars#expand(String)}.)
 *
 * <p>
 * To contribute an extension point, put {@link Extension} on your {@link ToolDescriptor} class.
 *
 * @author huybrechts
 * @since 1.286
 */
public abstract class ToolInstallation implements Serializable, Describable<ToolInstallation>, ExtensionPoint {

    private final String name;
    private final String home;

    public ToolInstallation(String name, String home) {
        this.name = name;
        this.home = home;
    }

    /**
     * Gets the human readable name that identifies this tool among other {@link ToolInstallation}s of the same kind.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the home directory of this tool.
     * 
     * The path can be in Unix format as well as in Windows format.
     * Must be absolute.
     */
    public String getHome() {
        return home;
    }

    public ToolDescriptor<?> getDescriptor() {
        return (ToolDescriptor) Hudson.getInstance().getDescriptor(getClass());
    }

    /**
     * Returns all the registered {@link ToolDescriptor}s.
     */
    public static DescriptorExtensionList<ToolInstallation,ToolDescriptor<?>> all() {
        // use getDescriptorList and not getExtensionList to pick up legacy instances
        return Hudson.getInstance().getDescriptorList(ToolInstallation.class);
    }

    private static final long serialVersionUID = 1L;
}
