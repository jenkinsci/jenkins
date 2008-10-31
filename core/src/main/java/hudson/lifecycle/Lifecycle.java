package hudson.lifecycle;

import hudson.ExtensionPoint;
import hudson.model.Hudson;

import java.io.File;
import java.io.IOException;

/**
 * Provides the capability for starting/stopping/restarting/uninstalling Hudson.
 *
 * <p>
 * The steps to perform these operations depend on how Hudson is launched,
 * so the concrete instance of this method (which is VM-wide singleton) is discovered
 * by looking up a FQCN from the system property "hudson.lifecycle".
 *
 * @author Kohsuke Kawaguchi
 * @since 1.254
 */
public abstract class Lifecycle implements ExtensionPoint {
    private static Lifecycle INSTANCE = null;

    /**
     * Gets the singleton instance.
     *
     * @return never null
     */
    public synchronized static Lifecycle get() {
        if(INSTANCE==null) {
            String p = System.getProperty("hudson.lifecycle");
            if(p!=null) {
                try {
                    ClassLoader cl = Hudson.getInstance().getPluginManager().uberClassLoader;
                    INSTANCE = (Lifecycle)cl.loadClass(p).newInstance();
                } catch (InstantiationException e) {
                    InstantiationError x = new InstantiationError(e.getMessage());
                    x.initCause(e);
                    throw x;
                } catch (IllegalAccessException e) {
                    IllegalAccessError x = new IllegalAccessError(e.getMessage());
                    x.initCause(e);
                    throw x;
                } catch (ClassNotFoundException e) {
                    NoClassDefFoundError x = new NoClassDefFoundError(e.getMessage());
                    x.initCause(e);
                    throw x;
                }
            } else {
                // no lifecycle given. use the default one
                INSTANCE = new Lifecycle() {
                };
            }
        }

        return INSTANCE;
    }

    /**
     * If the location of <tt>hudson.war</tt> is known in this life cycle,
     * and if this file is writable while Hudson is running, return it location.
     * Otherwise return null to indicate that it is unknown.
     *
     * <p>
     * When a non-null value is returned, Hudson will offer an upgrade UI
     * to a newer version.
     */
    public File getHudsonWar() {
        String war = System.getProperty("executable-war");
        if(war!=null && new File(war).exists())
            return new File(war);
        return null;
    }

    /**
     * If this life cycle supports a restart of Hudson, do so.
     * Otherwise, throw {@link UnsupportedOperationException},
     * which is what the default implementation does.
     *
     * <p>
     * The restart operation may happen synchronously (in which case
     * this method will never return), or asynchronously (in which
     * case this method will successfully return.)
     *
     * <p>
     * Throw an exception if the operation fails unexpectedly.
     */
    public void restart() throws IOException, InterruptedException {
        throw new UnsupportedOperationException();
    }
}
