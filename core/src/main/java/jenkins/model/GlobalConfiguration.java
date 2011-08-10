package jenkins.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;

/**
 * Convenient base class for extensions that contributes to the system configuration page but nothing else.
 *
 * <p>
 * All {@link Descriptor}s are capable of contributing fragment to the system config page, so
 * this extension point is is really only for those who don't want to contribute anything else.
 * If you are implementing other extension points and that would like to expose some global configuration,
 * you can do so with <tt>global.groovy</tt> from your {@link Descriptor} instance.
 *
 * <h2>Views</h2>
 * <p>
 * Subtypes of this class should define <tt>config.groovy</tt> that gets pulled into the system configuration page.
 * 
 *
 * @author Kohsuke Kawaguchi
 * @since 1.425
 */
public abstract class GlobalConfiguration extends Descriptor<GlobalConfiguration> implements ExtensionPoint, Describable<GlobalConfiguration>  {
    protected GlobalConfiguration() {
        super(self());
    }

    public final Descriptor<GlobalConfiguration> getDescriptor() {
        return this;
    }

    /**
     * Unless this object has additional web presence, display name is not used at all.
     * So default to "".
     */
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getGlobalConfigPage() {
        return getConfigPage();
    }

    /**
     * Returns all the registered {@link GlobalConfiguration} descriptors.
     */
    public static ExtensionList<GlobalConfiguration> all() {
        return Jenkins.getInstance().getDescriptorList(GlobalConfiguration.class);
    }
}
