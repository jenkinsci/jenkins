package jenkins.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;

/**
 * Convenient base class for extensions that contributes to the system configuration page but nothing
 * else, or to manage the global configuration of a plugin implementing several extension points.
 *
 * <p>
 * All {@link Descriptor}s are capable of contributing fragment to the system config page. If you are
 * implementing other extension points that need to expose some global configuration, you can do so
 * with <tt>global.groovy</tt> or <tt>global.jelly</tt> from your {@link Descriptor} instance. However
 * each <tt>global.*</tt> file will appear as its own section in the global configuration page.
 * 
 * <p>
 * An option to present a single section for your plugin in the Jenkins global configuration page is
 * to use this class to manage the configuration for your plugin and its extension points. To access
 * properties defined in your GlobalConfiguration subclass, here are two possibilities:
 * <ul><li>@{@link javax.inject.Inject} into your other {@link hudson.Extension}s (so this does <i>not</i> work
 * for classes not annotated with {@link hudson.Extension})</li>
 * <li>access it via a call to <code>GlobalConfiguration.all().get(&lt;your GlobalConfiguration subclass&gt;.class)
 * </code></li></ul>
 *
 * <h2>Views</h2>
 * <p>
 * Subtypes of this class should define a <tt>config.groovy</tt> file or <tt>config.jelly</tt> file
 * that gets pulled into the system configuration page.
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

    @Override
    public String getGlobalConfigPage() {
        return getConfigPage();
    }

    /**
     * Returns all the registered {@link GlobalConfiguration} descriptors.
     */
    public static ExtensionList<GlobalConfiguration> all() {
        return Jenkins.getInstance().<GlobalConfiguration,GlobalConfiguration>getDescriptorList(GlobalConfiguration.class);
        // pointless type parameters help work around bugs in javac in earlier versions http://codepad.org/m1bbFRrH
    }
}
