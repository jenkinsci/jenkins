package jenkins.mvn;

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.TaskListener;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author Dominik Bartholdi (imod)
 * @since 1.491
 */
public abstract class GlobalSettingsProvider extends AbstractDescribableImpl<GlobalSettingsProvider> implements ExtensionPoint {

    /**
     * configure maven launcher argument list with adequate settings path
     * 
     * @param build
     *            the build to provide the settigns for
     * @return the filepath to the provided file. <code>null</code> if no settings will be provided.
     */
    public abstract FilePath supplySettings(AbstractBuild<?, ?> build, TaskListener listener);

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
            try {
                settingsPath = settings.supplySettings(build, listener);
            } catch (Exception e) {
                listener.getLogger().print("failed to get the path to the alternate global settings.xml");
            }
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
