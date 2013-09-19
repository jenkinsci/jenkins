package jenkins.model;

import hudson.Extension;
import hudson.Util;
import hudson.XmlFile;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores the location of Jenkins (e-mail address and the HTTP URL.)
 *
 * @author Kohsuke Kawaguchi
 * @since 1.494
 */
@Extension
public class JenkinsLocationConfiguration extends GlobalConfiguration {
    /**
     * @deprecated
     */
    private transient String hudsonUrl;
    private String adminAddress;
    private String jenkinsUrl;

    // just to suppress warnings
    private transient String charset,useSsl;

    public static JenkinsLocationConfiguration get() {
        return GlobalConfiguration.all().get(JenkinsLocationConfiguration.class);
    }

    public JenkinsLocationConfiguration() {
        load();
    }

    @Override
    public synchronized void load() {
        // for backward compatibility, if we don't have our own data yet, then
        // load from Mailer.
        XmlFile file = getConfigFile();
        if(!file.exists()) {
            XStream2 xs = new XStream2();
            xs.addCompatibilityAlias("hudson.tasks.Mailer$DescriptorImpl",JenkinsLocationConfiguration.class);
            file = new XmlFile(xs,new File(Jenkins.getInstance().getRootDir(),"hudson.tasks.Mailer.xml"));
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
        String v = adminAddress;
        if(v==null)     v = Messages.Mailer_Address_Not_Configured();
        return v;
    }

    public void setAdminAddress(String adminAddress) {
        if(adminAddress.startsWith("\"") && adminAddress.endsWith("\"")) {
            // some users apparently quote the whole thing. Don't konw why
            // anyone does this, but it's a machine's job to forgive human mistake
            adminAddress = adminAddress.substring(1,adminAddress.length()-1);
        }
        this.adminAddress = adminAddress;
        save();
    }

    public String getUrl() {
        return jenkinsUrl;
    }

    public void setUrl(String hudsonUrl) {
        String url = Util.nullify(hudsonUrl);
        if(url!=null && !url.endsWith("/"))
            url += '/';
        this.jenkinsUrl = url;
        save();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this,json);
        return true;
    }

    /**
     * Checks the URL in <tt>global.jelly</tt>
     */
    public FormValidation doCheckUrl(@QueryParameter String value) {
        if(value.startsWith("http://localhost"))
            return FormValidation.warning(Messages.Mailer_Localhost_Error());
        return FormValidation.ok();
    }

    public FormValidation doCheckAdminAddress(@QueryParameter String value) {
        try {
            new InternetAddress(value);
            return FormValidation.ok();
        } catch (AddressException e) {
            return FormValidation.error(e.getMessage());
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JenkinsLocationConfiguration.class.getName());
}
