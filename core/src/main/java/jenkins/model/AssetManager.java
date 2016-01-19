package jenkins.model;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.util.TimeUnit2;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;

/**
 * Serves files located in the {@code /assets} classpath directory via the Jenkins core ClassLoader.
 * e.g. the URL {@code /assets/jquery-detached/jsmodules/jquery2.js} will load {@code jquery-detached/jsmodules/jquery2.js}
 * resource from the classpath below {@code /assets}.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class AssetManager implements UnprotectedRootAction {

    // not shown in the UI
    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return "assets";
    }

    /**
     * Exposes assets in the core classloader over HTTP.
     */
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String path = req.getRestOfPath();
        URL resource = findResource(path);

        if (resource == null) {
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Stapler routes requests like the "/static/.../foo/bar/zot" to be treated like "/foo/bar/zot"
        // and this is used to serve long expiration header, by using Jenkins.VERSION_HASH as "..."
        // to create unique URLs. Recognize that and set a long expiration header.
        String requestPath = req.getRequestURI().substring(req.getContextPath().length());
        boolean staticLink = requestPath.startsWith("/static/");
        long expires = staticLink ? TimeUnit2.DAYS.toMillis(365) : -1;

        // use serveLocalizedFile to support automatic locale selection
        rsp.serveLocalizedFile(req, resource, expires);
    }

    /**
     * Locates the asset from the classloader.
     *
     * <p>
     * To allow plugins to bring its own assets without worrying about colliding with the assets in core,
     * look for child classloader first. But to support plugins that get split, if the child classloader
     * doesn't find it, fall back to the parent classloader.
     */
    private URL findResource(String path) throws IOException {
        try {
            if (path.contains("..")) // crude avoidance of directory traversal attack
                throw new IllegalArgumentException(path);

            String name;
            if (path.charAt(0) == '/') {
                name = "assets" + path;
            } else {
                name = "assets/" + path;
            }

            ClassLoader cl = Jenkins.class.getClassLoader();
            URL url = (URL) $findResource.invoke(cl, name);
            if (url==null) {
                // pick the last one, which is the one closest to the leaf of the classloader tree.
                Enumeration<URL> e = cl.getResources(name);
                while (e.hasMoreElements()) {
                    url = e.nextElement();
                }
            }
            return url;
        } catch (InvocationTargetException|IllegalAccessException e) {
            throw new Error(e);
        }
    }

    private static final Method $findResource = init();

    private static Method init() {
        try {
            Method m = ClassLoader.class.getDeclaredMethod("findResource", String.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw (Error)new NoSuchMethodError().initCause(e);
        }
    }
}
