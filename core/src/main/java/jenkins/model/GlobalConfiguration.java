package jenkins.model;

import java.io.IOException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.security.Permission;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;

/**
 * Convenient base class for extensions that contributes to the system configuration page but nothing
 * else, or to manage the global configuration of a plugin implementing several extension points.
 *
 * <p>
 * All {@link Descriptor}s are capable of contributing fragment to the system config page. If you are
 * implementing other extension points that need to expose some global configuration, you can do so
 * with {@code global.groovy} or {@code global.jelly} from your {@link Descriptor} instance. However
 * each {@code global.*} file will appear as its own section in the global configuration page.
 * 
 * <p>
 * An option to present a single section for your plugin in the Jenkins global configuration page is
 * to use this class to manage the configuration for your plugin and its extension points. To access
 * properties defined in your GlobalConfiguration subclass, here are two possibilities:
 * <ul><li>@{@link javax.inject.Inject} into your other {@link hudson.Extension}s (so this does <i>not</i> work
 * for classes not annotated with {@link hudson.Extension})</li>
 * <li>access it via a call to {@code ExtensionList.lookupSingleton(<your GlobalConfiguration subclass>.class)}</li></ul>
 *
 * <p>
 * While an implementation might store its actual configuration data in various ways,
 * meaning {@link #configure(StaplerRequest, JSONObject)} must be overridden,
 * in the normal case you would simply define persistable fields with getters and setters.
 * The {@code config} view would use data-bound controls like {@code f:entry}.
 * Then make sure your constructor calls {@link #load} and your setters call {@link #save}.
 *
 * <h2>Views</h2>
 * <p>
 * Subtypes of this class should define a {@code config.groovy} file or {@code config.jelly} file
 * that gets pulled into the system configuration page.
 * Typically its contents should be wrapped in an {@code f:section}.
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
     * By default, calls {@link StaplerRequest#bindJSON(Object, JSONObject)},
     * appropriate when your implementation has getters and setters for all fields.
     * <p>{@inheritDoc}
     */
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        return true;
    }

    /**
     * Returns all the registered {@link GlobalConfiguration} descriptors.
     */
    public static @Nonnull ExtensionList<GlobalConfiguration> all() {
        return Jenkins.get().getDescriptorList(GlobalConfiguration.class);
        // pointless type parameters help work around bugs in javac in earlier versions http://codepad.org/m1bbFRrH
    }

    /**
     * Returns the permission type needed in order to use/access this
     * By default,  require Jenkins.ADMINISTER permission
     * Ovveride to return something different if appropriate
     * @return Permission requiered to use/access this
     */
    public @Nonnull Permission getPermission() {
        return Jenkins.ADMINISTER;
    }

    @Extension
    public static class GlobalConfigHiddenByPermissionFilter extends DescriptorVisibilityFilter {

        @SuppressWarnings("rawtypes")
        @Override
        public boolean filter(Object context, Descriptor descriptor) {
            boolean result = false;
            try {
                if (descriptor instanceof GlobalConfiguration) {
                    return Functions.hasPermission(((GlobalConfiguration) descriptor).getPermission());
                } else {
                    return true;
                }
            } catch (AccessDeniedException e) {
                return false;
            } catch (ServletException | IOException e) {
                e.printStackTrace();
                return true;
            }
        }
    }
}
