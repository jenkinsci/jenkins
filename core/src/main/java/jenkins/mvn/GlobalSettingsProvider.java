package jenkins.mvn;

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author Dominik Bartholdi (imod)
 * @since 1.491
 */
public abstract class GlobalSettingsProvider extends AbstractDescribableImpl<GlobalSettingsProvider> implements ExtensionPoint {

    /**
     * Configure maven launcher argument list with adequate settings path.
     *
     * <p>Implementations should
     * <ul>Be aware that this method might get called multiple times during a build.</ul>
     * <ul>Implement this method. This class provides a default implementation throwing an {@link UnsupportedOperationException}
     * so that implementations have time to adapt.</ul>
     * </p>
     *
     * @param run       the build / run to provide the settings for
     * @param workspace the workspace in which the build / run takes place
     * @param listener the listener of this given build / run
     * @return the filepath to the provided file. <code>null</code> if no settings will be provided.
     * @throws IOException typically occurs when the {@code supplySettings()} implementation accesses to the
     *         build environment on the build agent (copying a file to disk...)
     * @throws InterruptedException typically occurs while the {@code supplySettings()} implementation accesses to the
     *         build environment on the build agent (copying a file to disk...)
     * @since TODO
     */
    @CheckForNull
    public FilePath supplySettings(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        if (run instanceof AbstractBuild && Util.isOverridden(GlobalSettingsProvider.class, this.getClass() , "supplySettings",AbstractBuild.class, TaskListener.class)) {
            AbstractBuild build = (AbstractBuild) run;
            return supplySettings(build, listener);
        } else {
            throw new AbstractMethodError("Class " + getClass() + " must override the new method supplySettings(Run<?, ?> run, FilePath workspace, TaskListener listener)");
        }
    }

    /**
     * configure maven launcher argument list with adequate settings path
     * 
     * @param build
     *            the build to provide the settings for
     * @return the filepath to the provided file. <code>null</code> if no settings will be provided.
     * @throws RuntimeException if an {@link IOException} or an {@link InterruptedException} occurs. These {@link IOException}
     *         or {@link InterruptedException} can typically occur when the {@code supplySettings()} implementation access to the
     *         build environment on the build agent (copying a file to disk...)
     * @deprecated use {@link #supplySettings(Run, FilePath, TaskListener)}
     */
    @Deprecated
    public FilePath supplySettings(AbstractBuild<?, ?> build, TaskListener listener) {
        try {
            return supplySettings(build, build.getWorkspace(), listener);
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare Maven global settings.xml for " + build +
                    " in workspace " + build.getWorkspace(), e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to prepare Maven global settings.xml for " + build +
                    " in workspace " + build.getWorkspace(), e);
        }
    }

    public static GlobalSettingsProvider parseSettingsProvider(StaplerRequest req) throws Descriptor.FormException, ServletException {
        JSONObject settings = req.getSubmittedForm().getJSONObject("globalSettings");
        if (settings == null) {
            return new DefaultGlobalSettingsProvider();
        }
        return req.bindJSON(GlobalSettingsProvider.class, settings);
    }

    /**
     * Convenience method handling all <code>null</code> checks. Provides the path on the (possible) remote settings file.
     * 
     * @param settings
     *            the provider to be used
     * @param build
     *            the active build
     * @param listener
     *            the listener of the current build
     * @return the path to the global settings.xml
     */
    public static final FilePath getSettingsFilePath(GlobalSettingsProvider settings, AbstractBuild<?, ?> build, TaskListener listener) {
        FilePath settingsPath = null;
        if (settings != null) {
            settingsPath = settings.supplySettings(build, listener);
        }
        return settingsPath;
    }

    /**
     * Convenience method handling all <code>null</code> checks. Provides the path on the (possible) remote settings file.
     * 
     * @param provider
     *            the provider to be used
     * @param build
     *            the active build
     * @param listener
     *            the listener of the current build
     * @return the path to the global settings.xml
     */
    public static final String getSettingsRemotePath(GlobalSettingsProvider provider, AbstractBuild<?, ?> build, TaskListener listener) {
        FilePath fp = getSettingsFilePath(provider, build, listener);
        return fp == null ? null : fp.getRemote();
    }

}
