/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Tom Huybrechts
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
import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.*;
import hudson.slaves.NodeSpecific;
import hudson.util.DescribableList;
import hudson.util.XStream2;

import java.io.Serializable;
import java.io.IOException;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamSerializable;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;

/**
 * Formalization of a tool installed in nodes used for builds.
 * (Examples include things like JDKs, Ants, Mavens, and Groovys.)
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
 * (by using {@link #translateFor(Node, TaskListener)}) and
 * {@link EnvironmentSpecific} (by using {@link EnvVars#expand(String)}.)
 * Callers such as build steps can then use {@link #translate(AbstractBuild,TaskListener)}
 * and cast to the desired {@link ToolInstallation} subtype, or just call
 * {@link NodeSpecific#forNode} and {@link EnvironmentSpecific#forEnvironment} directly.
 *
 * <p>
 * To contribute an extension point, put {@link Extension} on your {@link ToolDescriptor} class.
 *
 * @author huybrechts
 * @since 1.286
 */
public abstract class ToolInstallation extends AbstractDescribableImpl<ToolInstallation> implements Serializable, ExtensionPoint {
    private final String name;
    private /*almost final*/ String home;

    /**
     * {@link ToolProperty}s that are associated with this tool.
     */
    @XStreamSerializable
    private transient /*almost final*/ DescribableList<ToolProperty<?>,ToolPropertyDescriptor> properties
            = new DescribableList<ToolProperty<?>,ToolPropertyDescriptor>(Saveable.NOOP);

    /**
     * @deprecated
     *      as of 1.302. Use {@link #ToolInstallation(String, String, List)} 
     */
    @Deprecated
    public ToolInstallation(String name, String home) {
        this.name = name;
        this.home = home;
    }

    public ToolInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        this.name = name;
        this.home = home;
        if(properties!=null) {
            try {
                this.properties.replaceBy(properties);
                for (ToolProperty<?> p : properties)
                    _setTool(p,this);
            } catch (IOException e) {
                throw new AssertionError(e); // no Saveable, so can't happen
            }
        }
    }

    // helper function necessary to avoid a warning
    private <T extends ToolInstallation> void _setTool(ToolProperty<T> prop, ToolInstallation t) {
        prop.setTool(prop.type().cast(t));
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
     * @return the home directory location, if defined (may only be defined on the result of {@link #translate(Node, EnvVars, TaskListener)}, e.g. if unavailable on master)
     */
    public @CheckForNull String getHome() {
        return home;
    }

    /**
     * Expose any environment variables that this tool installation wants the build to see.
     *
     * <p>
     * To add entry to PATH, do {@code envVars.put("PATH+XYZ",path)} where 'XYZ' is something unique.
     * Variable names of the form 'A+B' is interpreted as adding the value to the existing PATH.
     *
     * @since 1.460
     */
    public void buildEnvVars(EnvVars env) {
    }

    public DescribableList<ToolProperty<?>,ToolPropertyDescriptor> getProperties() {
        assert properties!=null;
        return properties;
    }

    /**
     * Performs a necessary variable/environment/context expansion.
     *
     * @param node
     *      Node that this tool is used in.
     * @param envs
     *      Set of environment variables to expand any references.
     * @param listener
     *      Any lengthy operation (such as auto-installation) will report its progress here.
     * @return
     *      {@link ToolInstallation} object that is fully specialized.
     * @see NodeSpecific
     * @see EnvironmentSpecific
     * @since 1.460
     */
    public ToolInstallation translate(@Nonnull Node node, EnvVars envs, TaskListener listener) throws IOException, InterruptedException {
        ToolInstallation t = this;
        if (t instanceof NodeSpecific) {
            NodeSpecific n = (NodeSpecific) t;
            t = (ToolInstallation)n.forNode(node,listener);
        }
        if (t instanceof EnvironmentSpecific) {
            EnvironmentSpecific e = (EnvironmentSpecific) t;
            t = (ToolInstallation)e.forEnvironment(envs);
        }
        return t;
    }

    /**
     * Convenient version of {@link #translate(Node, EnvVars, TaskListener)} that just takes a build object in progress.
     * @since 1.460
     */
    public ToolInstallation translate(AbstractBuild<?,?> buildInProgress, TaskListener listener) throws IOException, InterruptedException {
        assert buildInProgress.isBuilding();
        return translate(buildInProgress.getBuiltOn(),buildInProgress.getEnvironment(listener),listener);
    }

    /**
     * Finds a tool on a node.
     * Checks if the location of the tool is overridden for the given node, and if so,
     * return the node-specific home directory.
     * Also checks available {@link ToolLocationTranslator}s.
     * Otherwise returns {@code installation.getHome()}.
     *
     * <p>
     * This is the core logic behind {@link NodeSpecific#forNode(Node, TaskListener)} for {@link ToolInstallation},
     * and meant to be used by the {@code forNode} implementations.
     *
     * @return
     *      never null.
     */
    @SuppressWarnings("deprecation")
    protected String translateFor(Node node, TaskListener log) throws IOException, InterruptedException {
        return ToolLocationNodeProperty.getToolHome(node, this, log);
    }

    /**
     * Invoked by XStream when this object is read into memory.
     */
    protected Object readResolve() {
        if(properties==null)
            properties = new DescribableList<ToolProperty<?>,ToolPropertyDescriptor>(Saveable.NOOP);
        for (ToolProperty<?> p : properties)
            _setTool(p, this);
        return this;
    }

    @Override public String toString() {
        return getClass().getSimpleName() + "[" + name + "]";
    }

    /**
     * Subclasses can extend this for data migration from old field storing home directory.
     */
    protected static abstract class ToolConverter extends XStream2.PassthruConverter<ToolInstallation> {
        public ToolConverter(XStream2 xstream) { super(xstream); }
        protected void callback(ToolInstallation obj, UnmarshallingContext context) {
            String s;
            if (obj.home == null && (s = oldHomeField(obj)) != null) {
                obj.home = s;
                OldDataMonitor.report(context, "1.286");
            }
        }
        protected abstract String oldHomeField(ToolInstallation obj);
    }

    /**
     * Returns all the registered {@link ToolDescriptor}s.
     */
    public static DescriptorExtensionList<ToolInstallation,ToolDescriptor<?>> all() {
        // use getDescriptorList and not getExtensionList to pick up legacy instances
        return Jenkins.getInstance().<ToolInstallation,ToolDescriptor<?>>getDescriptorList(ToolInstallation.class);
    }

    private static final long serialVersionUID = 1L;
}
