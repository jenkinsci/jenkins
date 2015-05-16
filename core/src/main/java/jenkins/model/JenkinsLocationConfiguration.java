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
import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.Util.fixNull;

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

        updateSecureSessionFlag();
    }

    public String getAdminAddress() {
        String v = adminAddress;
        if(v==null)     v = Messages.Mailer_Address_Not_Configured();
        return v;
    }

    /**
     * Sets the e-mail address of Jenkins administrator.
     * @param adminAddress Admin address. Use null to reset the value to default.
     */
    public void setAdminAddress(@CheckForNull String adminAddress) {
        String address = Util.nullify(adminAddress);
        if(address != null && address.startsWith("\"") && address.endsWith("\"")) {
            // some users apparently quote the whole thing. Don't konw why
            // anyone does this, but it's a machine's job to forgive human mistake
            address = address.substring(1,address.length()-1);
        }
        this.adminAddress = address;
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
        updateSecureSessionFlag();
    }

    /**
     * If the Jenkins URL starts from "https", force the secure session flag
     *
     * @see <a href="https://www.owasp.org/index.php/SecureFlag">discussion of this topic in OWASP</a>
     */
    private void updateSecureSessionFlag() {
        try {
            ServletContext context = Jenkins.getInstance().servletContext;
            Method m;
            try {
                m = context.getClass().getMethod("getSessionCookieConfig");
            } catch (NoSuchMethodException x) { // 3.0+
                LOGGER.log(Level.FINE, "Failed to set secure cookie flag", x);
                return;
            }
            Object sessionCookieConfig = m.invoke(context);

            Class scc = Class.forName("javax.servlet.SessionCookieConfig");
            Method setSecure = scc.getMethod("setSecure", boolean.class);
            boolean v = fixNull(jenkinsUrl).startsWith("https");
            setSecure.invoke(sessionCookieConfig, v);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof IllegalStateException) {
                // servlet 3.0 spec seems to prohibit this from getting set at runtime,
                // though Winstone is happy to accept i. see JENKINS-25019
                return;
            }
            LOGGER.log(Level.WARNING, "Failed to set secure cookie flag", e);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set secure cookie flag", e);
        }
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
