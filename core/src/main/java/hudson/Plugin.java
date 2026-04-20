/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

package hudson;

import com.thoughtworks.xstream.XStream;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.init.Initializer;
import hudson.init.Terminator;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Saveable;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SaveableListener;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.model.Loadable;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Base class of Hudson plugin.
 *
 * <p>
 * A plugin may {@linkplain #Plugin derive from this class}, or it may directly define extension
 * points annotated with {@link hudson.Extension}. For a list of extension
 * points, see <a href="https://www.jenkins.io/redirect/developer/extension-points">
 * https://www.jenkins.io/redirect/developer/extension-points</a>.
 *
 * <p>
 * One instance of a plugin is created by Hudson, and used as the entry point
 * to plugin functionality.
 *
 * <p>
 * A plugin is bound to URL space of Hudson as {@code ${rootURL}/plugin/foo/},
 * where "foo" is taken from your plugin name "foo.jpi". All your web resources
 * in src/main/webapp are visible from this URL, and you can also define Jelly
 * views against your Plugin class, and those are visible in this URL, too.
 *
 * <p>
 * {@link Plugin} can have an optional {@code config.jelly} page. If present,
 * it will become a part of the system configuration page (http://server/hudson/configure).
 * This is convenient for exposing/maintaining configuration that doesn't
 * fit any {@link Descriptor}s.
 *
 * <p>
 * Up until Hudson 1.150 or something, subclasses of {@link Plugin} required
 * {@code @plugin} javadoc annotation, but that is no longer a requirement.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.42
 */
public abstract class Plugin implements Loadable, Saveable, StaplerProxy {

    private static final Logger LOGGER = Logger.getLogger(Plugin.class.getName());

    /**
     * You do not need to create custom subtypes:
     * <ul>
     * <li>{@code config.jelly}, {@link #configure(StaplerRequest2, JSONObject)}, {@link #load}, and {@link #save}
     *      can be replaced by {@link GlobalConfiguration}
     * <li>{@link #start} and {@link #postInitialize} can be replaced by {@link Initializer} (or {@link ItemListener#onLoaded})
     * <li>{@link #stop} can be replaced by {@link Terminator}
     * <li>{@link #setServletContext} can be replaced by {@link Jenkins#getServletContext}
     * </ul>
     * Note that every plugin gets a {@link DummyImpl} by default,
     * which will still route the URL space, serve {@link #getWrapper}, and so on.
     * @deprecated Use more modern APIs rather than subclassing.
     */
    @Deprecated
    protected Plugin() {}

    /**
     * Set by the {@link PluginManager}, before the {@link #start()} method is called.
     * This points to the {@link PluginWrapper} that wraps
     * this {@link Plugin} object.
     */
    /*package*/ transient PluginWrapper wrapper;

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
     * Gets the paired {@link PluginWrapper}.
     *
     * @since 1.426
     */
    public PluginWrapper getWrapper() {
        return wrapper;
    }

    /**
     * Called to allow plugins to initialize themselves.
     *
     * <p>
     * This method is called after {@link #setServletContext(ServletContext)} is invoked.
     * You can also use {@link jenkins.model.Jenkins#get()} to access the singleton Jenkins instance,
     * although the plugin start up happens relatively early in the initialization
     * stage and not all the data are loaded in Jenkins.
     *
     * <p>
     * If a plugin wants to run an initialization step after all plugins and extension points
     * are registered, a good place to do that is {@link #postInitialize()}.
     * If a plugin wants to run an initialization step after all the jobs are loaded,
     * {@link ItemListener#onLoaded()} is a good place.
     *
     * @throws Exception
     *      any exception thrown by the plugin during the initialization will disable plugin.
     *
     * @since 1.42
     * @see ExtensionPoint
     * @see #postInitialize()
     */
    public void start() throws Exception {
    }

    /**
     * Called after {@link #start()} is called for all the plugins.
     *
     * @throws Exception
     *      any exception thrown by the plugin during the initialization will disable plugin.
     */
    public void postInitialize() throws Exception {}

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
     * @since 1.233
     * @deprecated as of 1.305 override {@link #configure(StaplerRequest2,JSONObject)} instead
     */
    @Deprecated
    public void configure(JSONObject formData) throws IOException, javax.servlet.ServletException, FormException {
    }

    /**
     * Handles the submission for the system configuration.
     *
     * <p>
     * If this class defines {@code config.jelly} view, be sure to
     * override this method and persists the submitted values accordingly.
     *
     * <p>
     * The following is a sample {@code config.jelly} that you can start yours with:
     * <pre>{@code <xmp>
     * <j:jelly xmlns:j="jelly:core" xmlns:st="jelly:stapler" xmlns:d="jelly:define" xmlns:l="/lib/layout" xmlns:t="/lib/hudson" xmlns:f="/lib/form">
     *   <f:section title="Locale">
     *     <f:entry title="${%Default Language}" help="/plugin/locale/help/default-language.html">
     *       <f:textbox name="systemLocale" value="${it.systemLocale}" />
     *     </f:entry>
     *   </f:section>
     * </j:jelly>
     * </xmp>}</pre>
     *
     * <p>
     * This allows you to access data as {@code formData.getString("systemLocale")}
     *
     * <p>
     * If you are using this method, you'll likely be interested in
     * using {@link #save()} and {@link #load()}.
     * @since 2.475
     */
    public void configure(StaplerRequest2 req, JSONObject formData) throws IOException, ServletException, FormException {
        try {
            if (Util.isOverridden(Plugin.class, getClass(), "configure", StaplerRequest.class, JSONObject.class)) {
                configure(StaplerRequest.fromStaplerRequest2(req), formData);
            } else {
                configure(formData);
            }
        } catch (javax.servlet.ServletException e) {
            throw ServletExceptionWrapper.toJakartaServletException(e);
        }
    }

    /**
     * @deprecated use {@link #configure(StaplerRequest2, JSONObject)}
     * @since 1.305
     */
    @Deprecated
    public void configure(StaplerRequest req, JSONObject formData) throws IOException, javax.servlet.ServletException, FormException {
        configure(formData);
    }

    /**
     * This method serves static resources in the plugin under {@code hudson/plugin/SHORTNAME}.
     *
     * @since 2.475
     */
    public void doDynamic(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(Plugin.class, getClass(), "doDynamic", StaplerRequest.class, StaplerResponse.class)) {
            try {
                doDynamic(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            doDynamicImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #doDynamic(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doDynamicImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void doDynamicImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        String path = req.getRestOfPath();

        String pathUC = path.toUpperCase(Locale.ENGLISH);
        if (path.isEmpty() || path.contains("..") || path.startsWith(".") || path.contains("%")
                || pathUC.contains("META-INF") || pathUC.contains("WEB-INF")
                // ClassicPluginStrategy#explode produce that file to know if a new explosion is required or not
                || pathUC.equals("/.TIMESTAMP2")
        ) {
            LOGGER.warning("rejecting possibly malicious " + req.getRequestURIWithQueryString());
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // Stapler routes requests like the "/static/.../foo/bar/zot" to be treated like "/foo/bar/zot"
        // and this is used to serve long expiration header, by using Jenkins.VERSION_HASH as "..."
        // to create unique URLs. Recognize that and set a long expiration header.
        String requestPath = req.getRequestURI().substring(req.getContextPath().length());
        boolean staticLink = requestPath.startsWith("/static/");

        long expires = staticLink ? TimeUnit.DAYS.toMillis(365) : -1;

        // use serveLocalizedFile to support automatic locale selection
        rsp.serveLocalizedFile(req, new URL(wrapper.baseResourceURL, '.' + path), expires);
    }

//
// Convenience methods for those plugins that persist configuration
//
    /**
     * Loads serializable fields of this instance from the persisted storage.
     *
     * <p>
     * If there was no previously persisted state, this method is no-op.
     *
     * @since 1.245
     */
    @Override
    public synchronized void load() throws IOException {
        XmlFile xml = getConfigXml();
        if (xml.exists())
            xml.unmarshal(this);
    }

    /**
     * Saves serializable fields of this instance to the persisted storage.
     *
     * @since 1.245
     */
    @Override
    public synchronized void save() throws IOException {
        if (BulkChange.contains(this))   return;
        XmlFile config = getConfigXml();
        config.write(this);
        SaveableListener.fireOnChange(this, config);
    }

    /**
     * Controls the file where {@link #load()} and {@link #save()}
     * persists data.
     *
     * This method can be also overridden if the plugin wants to
     * use a custom {@link XStream} instance to persist data.
     *
     * @since 1.245
     */
    protected XmlFile getConfigXml() {
        return new XmlFile(Jenkins.XSTREAM,
                new File(Jenkins.get().getRootDir(), wrapper.getShortName() + ".xml"));
    }

    @Override
    @Restricted(NoExternalUse.class)
    public Object getTarget() {
        if (!SKIP_PERMISSION_CHECK) {
            Jenkins.get().checkPermission(Jenkins.READ);
        }
        return this;
    }

    /**
     * Escape hatch for StaplerProxy-based access control
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ boolean SKIP_PERMISSION_CHECK = SystemProperties.getBoolean(Plugin.class.getName() + ".skipPermissionCheck");

    /**
     * Dummy instance of {@link Plugin} to be used when a plugin didn't
     * supply one on its own.
     *
     * @since 1.321
     */
    public static final class DummyImpl extends Plugin {}
}
