package jenkins.model;

import hudson.Extension;
import hudson.XmlFile;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores the location of Jenkins (e-mail address and the HTTP URL.)
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class JenkinsLocationConfiguration extends GlobalConfiguration {
    /**
     * @deprecated
     */
    private transient String hudsonUrl;
    private String adminAddress;
    private String jenkinsUrl;

    public static JenkinsLocationConfiguration get() {
        return GlobalConfiguration.all().get(JenkinsLocationConfiguration.class);
    }

    @Override
    public synchronized void load() {
        // for backward compatibility, if we don't have our own data yet, then
        // load from Mailer.
        XmlFile file = getConfigFile();
        if(!file.exists()) {
            file = new XmlFile(new File(Jenkins.getInstance().getRootDir(),"hudson.tasks.Mailer.xml"));
            if (file.exists()) {
                try {
                    file.unmarshal(this);
                    if (jenkinsUrl==null)
                        jenkinsUrl = hudsonUrl;
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to load "+file, e);
                }
            }
        } else {
            super.load();
        }
    }

    public String getAdminAddress() {
        return adminAddress;
    }

    public void setAdminAddress(String adminAddress) {
        this.adminAddress = adminAddress;
        save();
    }

    public String getUrl() {
        return hudsonUrl;
    }

    public void setUrl(String hudsonUrl) {
        this.hudsonUrl = hudsonUrl;
        save();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this,json);
        return true;
    }

    private static final Logger LOGGER = Logger.getLogger(JenkinsLocationConfiguration.class.getName());
}
