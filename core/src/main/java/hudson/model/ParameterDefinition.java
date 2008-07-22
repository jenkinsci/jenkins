package hudson.model;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import hudson.util.DescriptorList;
import hudson.ExtensionPoint;

/**
 * Defines a parameter for a build.
 *
 * <p>
 * In Hudson, a user can configure a job to require parameters for a build.
 * For example, imagine a test job that takes the bits to be tested as a parameter.
 *
 * <p>
 * The actual meaning and the purpose of parameters are entirely up to users, so
 * what the concrete parameter implmentation is pluggable. Write subclasses
 * in a plugin and hook it up to {@link #LIST} to register it.
 *
 * <p>
 * Three classes are used to model build parameters. First is the
 * {@link ParameterDescriptor}, which tells Hudson what kind of implementations are
 * available. From {@link ParameterDescriptor#newInstance(StaplerRequest, JSONObject)},
 * Hudson creates {@link ParameterDefinition}s based on the job configuration.
 * For example, if the user defines two string parameters "database-type" and
 * "appserver-type", we'll get two {@link StringParameterDefinition} instances
 * with their respective names.
 *
 * <p>
 * When a job is configured with {@link ParameterDefinition} (or more precisely,
 * {@link ParametersDefinitionProperty}, which in turns retains {@link ParameterDefinition}s),
 * user would have to enter the values for the defined build parameters.
 * The {@link #createValue(StaplerRequest, JSONObject)} method is used to convert this
 * form submission into {@link ParameterValue} objects, which are then accessible
 * during a build.
 *
 *
 *
 * <h2>Persistence</h2>
 * <p>
 * Instances of {@link ParameterDefinition}s are persisted into job <tt>config.xml</tt>
 * through XStream.
 *
 *
 * <h2>Assocaited Views</h2>
 * <h4>config.jelly</h4>
 * <p>
 * {@link ParameterDefinition} class uses <tt>config.jelly</tt> to provide contribute a form
 * fragment in the job configuration screen. Values entered there is fed back to
 * {@link ParameterDescriptor#newInstance(StaplerRequest, JSONObject)} to create {@link ParameterDefinition}s.
 *
 * <h4>index.jelly</h4>
 * The <tt>index.jelly</tt> view contributes a form fragment in the page where the user
 * enters actual values of parameters for a build. The result of this form submission
 * is then fed to {@link ParameterDefinition#createValue(StaplerRequest, JSONObject)} to
 * create {@link ParameterValue}s.
 *
 * TODO: what Jelly pages does this object need for rendering UI?
 * TODO: {@link ParameterValue} needs to have some mechanism to expose values to the build
 * @see StringParameterDefinition
 */
public abstract class ParameterDefinition implements
        Describable<ParameterDefinition>, ExtensionPoint {

    private final String name;

    public String getName() {
        return name;
    }

    public ParameterDefinition(String name) {
        super();
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    public abstract ParameterDescriptor getDescriptor();

    public abstract ParameterValue createValue(StaplerRequest req, JSONObject jo);

    /**
     * A list of available parameter definition types
     */
    public static final DescriptorList<ParameterDefinition> LIST = new DescriptorList<ParameterDefinition>();

    public abstract static class ParameterDescriptor extends
            Descriptor<ParameterDefinition> {

        protected ParameterDescriptor(Class<? extends ParameterDefinition> klazz) {
            super(klazz);
        }

        public String getValuePage() {
            return getViewPage(clazz, "index.jelly");
        }

        @Override
        public String getDisplayName() {
            return "Parameter";
        }

    }

}
