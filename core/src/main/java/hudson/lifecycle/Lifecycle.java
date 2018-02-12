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
package hudson.lifecycle;

import hudson.ExtensionPoint;
import hudson.Functions;
import jenkins.util.SystemProperties;
import hudson.Util;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.apache.commons.io.FileUtils;

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
            Lifecycle instance;
            String p = SystemProperties.getString("hudson.lifecycle");
            if(p!=null) {
                try {
                    ClassLoader cl = Jenkins.getInstance().getPluginManager().uberClassLoader;
                    instance = (Lifecycle)cl.loadClass(p).newInstance();
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
                if(Functions.isWindows()) {
                    instance = new Lifecycle() {
                        @Override
                        public void verifyRestartable() throws RestartNotSupportedException {
                            throw new RestartNotSupportedException(
                                    "Default Windows lifecycle does not support restart.");
                        }
                    };
                } else if (System.getenv("SMF_FMRI")!=null && System.getenv("SMF_RESTARTER")!=null) {
                    // when we are run by Solaris SMF, these environment variables are set.
                    instance = new SolarisSMFLifecycle();
                } else {
                    // if run on Unix, we can do restart
                    try {
                        instance = new UnixLifecycle();
                    } catch (final IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to install embedded lifecycle implementation",e);
                        instance = new Lifecycle() {
                            @Override
                            public void verifyRestartable() throws RestartNotSupportedException {
                                throw new RestartNotSupportedException(
                                        "Failed to install embedded lifecycle implementation, so cannot restart: " + e, e);
                            }
                        };
                    }
                }
            }
            assert instance != null;
            INSTANCE = instance;
        }

        return INSTANCE;
    }

    /**
     * If the location of <tt>jenkins.war</tt> is known in this life cycle,
     * return it location. Otherwise return null to indicate that it is unknown.
     *
     * <p>
     * When a non-null value is returned, Hudson will offer an upgrade UI
     * to a newer version.
     */
    public File getHudsonWar() {
        String war = SystemProperties.getString("executable-war");
        if(war!=null && new File(war).exists())
            return new File(war);
        return null;
    }

    /**
     * Replaces jenkins.war by the given file.
     *
     * <p>
     * On some system, most notably Windows, a file being in use cannot be changed,
     * so rewriting <tt>jenkins.war</tt> requires some special trick. Override this method
     * to do so.
     */
    public void rewriteHudsonWar(File by) throws IOException {
        File dest = getHudsonWar();
        // this should be impossible given the canRewriteHudsonWar method,
        // but let's be defensive
        if(dest==null)  throw new IOException("jenkins.war location is not known.");

        // backing up the old jenkins.war before it gets lost due to upgrading
        // (newly downloaded jenkins.war and 'backup' (jenkins.war.tmp) are the same files
        // unless we are trying to rewrite jenkins.war by a backup itself
        File bak = new File(dest.getPath() + ".bak");
        if (!by.equals(bak))
            FileUtils.copyFile(dest, bak);
       
        FileUtils.copyFile(by, dest);
        // we don't want to keep backup if we are downgrading
        if (by.equals(bak)&&bak.exists())
            bak.delete();
    }

    /**
     * Can {@link #rewriteHudsonWar(File)} work?
     */
    public boolean canRewriteHudsonWar() {
        // if we don't know where jenkins.war is, it's impossible to replace.
        File f = getHudsonWar();
        if (f == null || !f.canWrite()) {
            return false;
        }
        File parent = f.getParentFile();
        if (parent == null || !parent.canWrite()) {
            return false;
        }
        return true;
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

    /**
     * Can the {@link #restart()} method restart Hudson?
     *
     * @throws RestartNotSupportedException
     *      If the restart is not supported, throw this exception and explain the cause.
     */
    public void verifyRestartable() throws RestartNotSupportedException {
        // the rewriteHudsonWar method isn't overridden.
        if (!Util.isOverridden(Lifecycle.class,getClass(), "restart"))
            throw new RestartNotSupportedException("Restart is not supported in this running mode (" +
                    getClass().getName() + ").");
    }

    /**
     * The same as {@link #verifyRestartable()} except the status is indicated by the return value,
     * not by an exception.
     */
    public boolean canRestart() {
        try {
            verifyRestartable();
            return true;
        } catch (RestartNotSupportedException e) {
            return false;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Lifecycle.class.getName());
}
