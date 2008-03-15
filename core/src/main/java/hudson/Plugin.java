package hudson;

import hudson.model.Hudson;
import hudson.scm.SCM;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;

/**
 * Base class of Hudson plugin.
 *
 * <p>
 * A plugin needs to derive from this class.
 *
 * <p>
 * One instance of a plugin is created by Hudson, and used as the entry point
 * to plugin functionality.
 *
 * <p>
 * A plugin is bound to URL space of Hudson as <tt>${rootURL}/plugin/foo/</tt>,
 * where "foo" is taken from your plugin name "foo.hpi". All your web resources
 * in src/main/webapp are visible from this URL, and you can also define Jelly
 * views against your Plugin class, and those are visible in this URL, too.
 *
 * <p>
 * Up until Hudson 1.150 or something, subclasses of {@link Plugin} required
 * <tt>@plugin</tt> javadoc annotation, but that is no longer a requirement.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.42
 */
public abstract class Plugin {

    /**
     * Set by the {@link PluginManager}.
     */
    /*package*/ PluginWrapper wrapper;

    /**
     * Called when a plugin is loaded to make the {@link ServletContext} object available to a plugin.
     * This object allows plugins to talk to the surrounding environment.
     *
     * <p>
     * The default implementation is no-op.
     *
     * @param context
     *      Always non-null.
     *
     * @since 1.42
     */
    public void setServletContext(ServletContext context) {
    }

    /**
     * Called to allow plugins to initialize themselves.
     *
     * <p>
     * This method is called after {@link #setServletContext(ServletContext)} is invoked.
     * You can also use {@link Hudson#getInstance()} to access the singleton hudson instance,
     * although the plugin start up happens relatively early in the initialization
     * stage and not all the data are loaded in Hudson.
     *
     * <p>
     * Plugins should override this method and register custom
     * {@link Publisher}, {@link Builder}, {@link SCM}, and {@link Trigger}s to the corresponding list.
     * See {@link ExtensionPoint} for the complete list of extension points in Hudson.
     *
     *
     * @throws Exception
     *      any exception thrown by the plugin during the initialization will disable plugin.
     *
     * @since 1.42
     * @see ExtensionPoint
     */
    public void start() throws Exception {
    }

    /**
     * Called to orderly shut down Hudson.
     *
     * <p>
     * This is a good opportunity to clean up resources that plugin started.
     * This method will not be invoked if the {@link #start()} failed abnormally.
     *
     * @throws Exception
     *      if any exception is thrown, it is simply recorded and shut-down of other
     *      plugins continue. This is primarily just a convenience feature, so that
     *      each plugin author doesn't have to worry about catching an exception and
     *      recording it.
     *
     * @since 1.42
     */
    public void stop() throws Exception {
    }

    /**
     * This method serves static resources in the plugin under <tt>hudson/plugin/SHORTNAME</tt>.
     */
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String path = req.getRestOfPath();

        if(path.length()==0)
            path = "/";

        if(path.indexOf("..")!=-1 || path.length()<1) {
            // don't serve anything other than files in the sub directory.
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // use serveLocalizedFile to support automatic locale selection
        rsp.serveLocalizedFile(req, new URL(wrapper.baseResourceURL,'.'+path));
    }
}
