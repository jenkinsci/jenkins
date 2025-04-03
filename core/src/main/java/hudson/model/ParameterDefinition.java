/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Luca Domenico Milanesio, Tom Huybrechts
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.cli.CLICommand;
import hudson.util.DescriptorList;
import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Defines a parameter for a build.
 *
 * <p>
 * In Jenkins, a user can configure a job to require parameters for a build.
 * For example, imagine a test job that takes the bits to be tested as a parameter.
 *
 * <p>
 * The actual meaning and the purpose of parameters are entirely up to users, so
 * what the concrete parameter implementation is pluggable. Write subclasses
 * in a plugin and put {@link Extension} on the descriptor to register them.
 *
 * <p>
 * Three classes are used to model build parameters. First is the
 * {@link ParameterDescriptor}, which tells Hudson what kind of implementations are
 * available. From {@link ParameterDescriptor#newInstance(StaplerRequest2, JSONObject)},
 * Hudson creates {@link ParameterDefinition}s based on the job configuration.
 * For example, if the user defines two string parameters "database-type" and
 * "appserver-type", we'll get two {@link StringParameterDefinition} instances
 * with their respective names.
 *
 * <p>
 * When a job is configured with {@link ParameterDefinition} (or more precisely,
 * {@link ParametersDefinitionProperty}, which in turns retains {@link ParameterDefinition}s),
 * user would have to enter the values for the defined build parameters.
 * The {@link #createValue(StaplerRequest2, JSONObject)} method is used to convert this
 * form submission into {@link ParameterValue} objects, which are then accessible
 * during a build.
 *
 *
 *
 * <h2>Persistence</h2>
 * <p>
 * Instances of {@link ParameterDefinition}s are persisted into job {@code config.xml}
 * through XStream.
 *
 *
 * <h2>Associated Views</h2>
 * <h3>config.jelly</h3>
 * {@link ParameterDefinition} class uses {@code config.jelly} to contribute a form
 * fragment in the job configuration screen. Values entered there are fed back to
 * {@link ParameterDescriptor#newInstance(StaplerRequest2, JSONObject)} to create {@link ParameterDefinition}s.
 *
 * <h3>index.jelly</h3>
 * The {@code index.jelly} view contributes a form fragment in the page where the user
 * enters actual values of parameters for a build. The result of this form submission
 * is then fed to {@link ParameterDefinition#createValue(StaplerRequest2, JSONObject)} to
 * create {@link ParameterValue}s.
 *
 * @see StringParameterDefinition
 */
@ExportedBean(defaultVisibility = 3)
public abstract class ParameterDefinition implements
        Describable<ParameterDefinition>, ExtensionPoint, Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;

    private String description;

    protected ParameterDefinition(@NonNull String name) {
        if (name == null) {
            throw new IllegalArgumentException("Parameter name must be non-null");
        }
        this.name = name;
    }

    /**
     * @deprecated Prefer {@link #ParameterDefinition(String)} with a {@link org.kohsuke.stapler.DataBoundConstructor} and allow {@link #setDescription} to be used as needed
     */
    @Deprecated
    protected ParameterDefinition(@NonNull String name, String description) {
        this(name);
        setDescription(description);
    }

    /**
     * Create a new instance of this parameter definition and use the passed
     * parameter value as the default value.
     *
     * @since 1.405
     */
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        // By default, just return this again
        return this;
    }

    @Exported
    public String getType() {
        return this.getClass().getSimpleName();
    }

    @Exported
    @NonNull
    public String getName() {
        return name;
    }

    @Exported
    @CheckForNull
    public String getDescription() {
        return description;
    }

    /**
     * @since 2.281
     */
    @DataBoundSetter
    public void setDescription(@CheckForNull String description) {
        this.description = Util.fixEmpty(description);
    }

    /**
     * return parameter description, applying the configured MarkupFormatter for jenkins instance.
     * @since 1.521
     */
    @CheckForNull
    public String getFormattedDescription() {
        try {
            return Jenkins.get().getMarkupFormatter().translate(getDescription());
        } catch (IOException e) {
            LOGGER.warning("failed to translate description using configured markup formatter");
            return "";
        }
    }

    @Override
    @NonNull
    public ParameterDescriptor getDescriptor() {
        return (ParameterDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Create a parameter value from a form submission.
     *
     * <p>
     * This method is invoked when the user fills in the parameter values in the HTML form
     * and submits it to the server.
     */
    @CheckForNull
    public /* abstract */ ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        return Util.ifOverridden(
                () -> createValue(StaplerRequest.fromStaplerRequest2(req), jo),
                ParameterDefinition.class,
                getClass(),
                "createValue",
                StaplerRequest.class,
                JSONObject.class);
    }

    /**
     * @deprecated use {@link #createValue(StaplerRequest2, JSONObject)}
     */
    @CheckForNull
    @Deprecated
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        return Util.ifOverridden(
                () -> createValue(StaplerRequest.toStaplerRequest2(req), jo),
                ParameterDefinition.class,
                getClass(),
                "createValue",
                StaplerRequest2.class,
                JSONObject.class);
    }

    /**
     * Create a parameter value from a GET with query string.
     * If no value is available in the request, it returns a default value if possible, or null.
     *
     * <p>
     * Unlike {@link #createValue(StaplerRequest2, JSONObject)}, this method is intended to support
     * the programmatic POST-ing of the build URL. This form is less expressive (as it doesn't support
     * the tree form), but it's more scriptable.
     *
     * <p>
     * If a {@link ParameterDefinition} can't really support this mode of creating a value,
     * you may just always return null.
     *
     * @throws IllegalStateException
     *      If the parameter is deemed required but was missing in the submission.
     */
    @CheckForNull
    public /* abstract */ ParameterValue createValue(StaplerRequest2 req) {
        return Util.ifOverridden(
                () -> createValue(StaplerRequest.fromStaplerRequest2(req)),
                ParameterDefinition.class,
                getClass(),
                "createValue",
                StaplerRequest.class);
    }

    /**
     * @deprecated use {@link #createValue(StaplerRequest2)}
     */
    @CheckForNull
    @Deprecated
    public ParameterValue createValue(StaplerRequest req) {
        return Util.ifOverridden(
                () -> createValue(StaplerRequest.toStaplerRequest2(req)),
                ParameterDefinition.class,
                getClass(),
                "createValue",
                StaplerRequest2.class);
    }

    /**
     * Create a parameter value from the string given in the CLI.
     *
     * @param command
     *      This is the command that got the parameter.
     * @throws AbortException
     *      If the CLI processing should be aborted. Hudson will report the error message
     *      without stack trace, and then exits this command. Useful for graceful termination.
     * @throws RuntimeException
     *      All the other exceptions cause the stack trace to be dumped, and then
     *      the command exits with an error code.
     * @since 1.334
     */
    @CheckForNull
    public ParameterValue createValue(CLICommand command, String value) throws IOException, InterruptedException {
        throw new AbortException("CLI parameter submission is not supported for the " + getClass() + " type. Please file a bug report for this");
    }

    /**
     * Returns default parameter value for this definition.
     *
     * @return default parameter value or null if no defaults are available
     * @since 1.253
     */
    @CheckForNull
    @Exported
    public ParameterValue getDefaultParameterValue() {
        return null;
    }

    /**
     * Checks whether a given value is valid for this definition.
     * @since 2.244
     * @param value The value to validate.
     * @return True if the value is valid for this definition. False if it is invalid.
     */
    public boolean isValid(ParameterValue value) {
        // The base implementation just accepts the value.
        return true;
    }

    @Override
    public int hashCode() {
        return Jenkins.XSTREAM2.toXML(this).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParameterDefinition other = (ParameterDefinition) obj;
        if (!Objects.equals(getName(), other.getName()))
            return false;
        if (!Objects.equals(getDescription(), other.getDescription()))
            return false;
        String thisXml  = Jenkins.XSTREAM2.toXML(this);
        String otherXml = Jenkins.XSTREAM2.toXML(other);
        return thisXml.equals(otherXml);
    }

    /**
     * Returns all the registered {@link ParameterDefinition} descriptors.
     */
    public static DescriptorExtensionList<ParameterDefinition, ParameterDescriptor> all() {
        return Jenkins.get().getDescriptorList(ParameterDefinition.class);
    }

    /**
     * A list of available parameter definition types
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access, and {@link Extension} for registration.
     */
    @Deprecated
    public static final DescriptorList<ParameterDefinition> LIST = new DescriptorList<>(ParameterDefinition.class);

    public abstract static class ParameterDescriptor extends
            Descriptor<ParameterDefinition> {

        protected ParameterDescriptor(Class<? extends ParameterDefinition> klazz) {
            super(klazz);
        }

        /**
         * Infers the type of the corresponding {@link ParameterDescriptor} from the outer class.
         * This version works when you follow the common convention, where a descriptor
         * is written as the static nested class of the describable class.
         *
         * @since 1.278
         */
        protected ParameterDescriptor() {
        }

        public String getValuePage() {
            return getViewPage(clazz, "index.jelly");
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Parameter";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ParameterDefinition.class.getName());
}
