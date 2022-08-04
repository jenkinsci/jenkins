package jenkins.model;

import static hudson.Util.fixNull;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.PersistentDescriptor;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import jakarta.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import jenkins.util.UrlHelper;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;

/**
 * Stores the location of Jenkins (e-mail address and the HTTP URL.)
 *
 * @author Kohsuke Kawaguchi
 * @since 1.494
 */
@Extension(ordinal = JenkinsLocationConfiguration.ORDINAL)
@Symbol("location")
public class JenkinsLocationConfiguration extends GlobalConfiguration implements PersistentDescriptor {

    /**
     * If disabled, the application will no longer check for URL validity in the configuration page.
     * This will lead to an instance vulnerable to SECURITY-1471.
     *
     * @since 2.176.4 / 2.197
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    public static /* not final */ boolean DISABLE_URL_VALIDATION =
            SystemProperties.getBoolean(JenkinsLocationConfiguration.class.getName() + ".disableUrlValidation");

    @Restricted(NoExternalUse.class)
    public static final int ORDINAL = 200;

    /**
     * @deprecated replaced by {@link #jenkinsUrl}
     */
    @Deprecated
    private transient String hudsonUrl;
    private String adminAddress;
    private String jenkinsUrl;

    // just to suppress warnings
    private transient String charset, useSsl;

    public static @NonNull JenkinsLocationConfiguration get() {
        return GlobalConfiguration.all().getInstance(JenkinsLocationConfiguration.class);
    }

    /**
     * Gets local configuration. For explanation when it could die, see {@link #get()}
     */
    @Restricted(NoExternalUse.class)
    public static @NonNull JenkinsLocationConfiguration getOrDie() {
        JenkinsLocationConfiguration config = JenkinsLocationConfiguration.get();
        if (config == null) {
            throw new IllegalStateException("JenkinsLocationConfiguration instance is missing. Probably the Jenkins instance is not fully loaded at this time.");
        }
        return config;
    }

    @Override
    public synchronized void load() {
        // for backward compatibility, if we don't have our own data yet, then
        // load from Mailer.
        XmlFile file = getConfigFile();
        if (!file.exists()) {
            XStream2 xs = new XStream2();
            xs.addCompatibilityAlias("hudson.tasks.Mailer$DescriptorImpl", JenkinsLocationConfiguration.class);
            file = new XmlFile(xs, new File(Jenkins.get().getRootDir(), "hudson.tasks.Mailer.xml"));
            if (file.exists()) {
                try {
                    file.unmarshal(this);
                    if (jenkinsUrl == null)
                        jenkinsUrl = hudsonUrl;
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to load " + file, e);
                }
            }
        } else {
            super.load();
        }

        if (!DISABLE_URL_VALIDATION) {
            preventRootUrlBeingInvalid();
        }

        updateSecureSessionFlag();
    }

    /**
     * Gets the service administrator e-mail address.
     * @return Admin address or &quot;address not configured&quot; stub
     */
    public @NonNull String getAdminAddress() {
        String v = adminAddress;
        if (v == null)     v = Messages.Mailer_Address_Not_Configured();
        return v;
    }

    /**
     * Sets the e-mail address of Jenkins administrator.
     * @param adminAddress Admin address. Use null to reset the value to default.
     */
    public void setAdminAddress(@CheckForNull String adminAddress) {
        String address = Util.fixEmptyAndTrim(adminAddress);
        if (address != null && address.startsWith("\"") && address.endsWith("\"")) {
            // some users apparently quote the whole thing. Don't know why
            // anyone does this, but it's a machine's job to forgive human mistake
            address = address.substring(1, address.length() - 1);
        }
        this.adminAddress = address;
        save();
    }

    public @CheckForNull String getUrl() {
        return jenkinsUrl;
    }

    public void setUrl(@CheckForNull String jenkinsUrl) {
        String url = Util.nullify(jenkinsUrl);
        if (url != null && !url.endsWith("/"))
            url += '/';
        this.jenkinsUrl = url;

        if (!DISABLE_URL_VALIDATION) {
            preventRootUrlBeingInvalid();
        }

        save();
        updateSecureSessionFlag();
    }

    private void preventRootUrlBeingInvalid() {
        if (this.jenkinsUrl != null && isInvalidRootUrl(this.jenkinsUrl)) {
            LOGGER.log(Level.INFO, "Invalid URL received: {0}, considered as null", this.jenkinsUrl);
            this.jenkinsUrl = null;
        }
    }

    private boolean isInvalidRootUrl(@Nullable String value) {
        return !UrlHelper.isValidRootUrl(value);
    }

    /**
     * If the Jenkins URL starts from "https", force the secure session flag
     *
     * @see <a href="https://www.owasp.org/index.php/SecureFlag">discussion of this topic in OWASP</a>
     */
    private void updateSecureSessionFlag() {
        try {
            ServletContext context = Jenkins.get().getServletContext();
            Method m;
            try {
                m = context.getClass().getMethod("getSessionCookieConfig");
            } catch (NoSuchMethodException x) { // 3.0+
                LOGGER.log(Level.FINE, "Failed to set secure cookie flag", x);
                return;
            }
            Object sessionCookieConfig = m.invoke(context);

            Class scc = Class.forName("jakarta.servlet.SessionCookieConfig");
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

    /**
     * Checks the URL in {@code global.jelly}
     */
    public FormValidation doCheckUrl(@QueryParameter String value) {
        if (value.startsWith("http://localhost"))
            return FormValidation.warning(Messages.Mailer_Localhost_Error());

        if (!DISABLE_URL_VALIDATION && isInvalidRootUrl(value)) {
            return FormValidation.error(Messages.Mailer_NotHttp_Error());
        }

        return FormValidation.ok();
    }

    public FormValidation doCheckAdminAddress(@QueryParameter String value) {
        // TODO if equal to Messages.Mailer_Address_Not_Configured(), suggest configuring it with FormValidation.warning?
        if (Util.fixNull(value).contains("@")) {
            return FormValidation.ok();
        } else {
            return FormValidation.error(Messages.JenkinsLocationConfiguration_does_not_look_like_an_email_address());
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JenkinsLocationConfiguration.class.getName());
}
