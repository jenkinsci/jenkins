/*
 * Copyright (c) 2008-2010 Yahoo! Inc.
 * All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.security.csrf;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.AdministrativeMonitor;
import hudson.model.ModelObject;
import hudson.model.PersistentDescriptor;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.HexStringConfidentialKey;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.core.Authentication;

/**
 * A crumb issuing algorithm based on the request principal and the session ID.
 *
 * @author dty
 */
public class DefaultCrumbIssuer extends CrumbIssuer {

    private transient MessageDigest md;
    private transient boolean excludeClientIPFromCrumb;

    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* non-final: Groovy Console */ boolean EXCLUDE_SESSION_ID = SystemProperties.getBoolean(DefaultCrumbIssuer.class.getName() + ".EXCLUDE_SESSION_ID");

    @DataBoundConstructor
    public DefaultCrumbIssuer() {
        initializeMessageDigest();
        logSessionIdWarningIfNeeded();
    }

    /**
     * @param excludeClientIPFromCrumb unused
     * @deprecated Use {@link #DefaultCrumbIssuer()} instead.
     */
    @Deprecated
    public DefaultCrumbIssuer(boolean excludeClientIPFromCrumb) {
        this();
        this.excludeClientIPFromCrumb = excludeClientIPFromCrumb;
        logSessionIdWarningIfNeeded();
    }

    private void logSessionIdWarningIfNeeded() {
        if (EXCLUDE_SESSION_ID) {
            LOGGER.warning("Jenkins no longer uses the client IP address as part of CSRF protection. " +
                    "This controller is configured to also not use the session ID (hudson.security.csrf.DefaultCrumbIssuer.EXCLUDE_SESSION_ID), which is even less safe now. " +
                    "This option will be removed in future releases.");
        }
    }

    /**
     * @return the previously set value
     * @deprecated This setting is no longer effective.
     */
    @Deprecated
    public boolean isExcludeClientIPFromCrumb() {
        return this.excludeClientIPFromCrumb;
    }

    private Object readResolve() {
        initializeMessageDigest();
        return this;
    }

    private synchronized void initializeMessageDigest() {
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            md = null;
            LOGGER.log(Level.SEVERE, e, () -> "Cannot find SHA-256 MessageDigest implementation.");
        }
    }

    @Override
    @SuppressFBWarnings(value = "NM_WRONG_PACKAGE", justification = "false positive")
    protected synchronized String issueCrumb(ServletRequest request, String salt) {
        if (request instanceof HttpServletRequest req) {
            if (md != null) {
                StringBuilder buffer = new StringBuilder();
                Authentication a = Jenkins.getAuthentication2();
                buffer.append(a.getName());
                if (!EXCLUDE_SESSION_ID) {
                    buffer.append(';');
                    buffer.append(req.getSession().getId());
                }

                md.update(buffer.toString().getBytes(StandardCharsets.UTF_8));
                return Util.toHexString(md.digest(salt.getBytes(StandardCharsets.US_ASCII)));
            }
        }
        return null;
    }

    @Override
    public boolean validateCrumb(ServletRequest request, String salt, String crumb) {
        if (request instanceof HttpServletRequest) {
            String newCrumb = issueCrumb(request, salt);
            if (newCrumb != null && crumb != null) {
                // String.equals() is not constant-time, but this is
                return MessageDigest.isEqual(newCrumb.getBytes(StandardCharsets.US_ASCII),
                        crumb.getBytes(StandardCharsets.US_ASCII));
            }
        }
        return false;
    }

    @Extension @Symbol("standard")
    public static final class DescriptorImpl extends CrumbIssuerDescriptor<DefaultCrumbIssuer> implements ModelObject, PersistentDescriptor {

        private static final HexStringConfidentialKey CRUMB_SALT = new HexStringConfidentialKey(Jenkins.class, "crumbSalt", 16);

        public DescriptorImpl() {
            super(CRUMB_SALT.get(), SystemProperties.getString("hudson.security.csrf.requestfield", CrumbIssuer.DEFAULT_CRUMB_NAME));
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.DefaultCrumbIssuer_DisplayName();
        }

        @Override
        public DefaultCrumbIssuer newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
            if (req == null) {
                // This state is prohibited according to the Javadoc of the super method.
                throw new FormException("DefaultCrumbIssuer new instance method is called for null Stapler request. "
                        + "Such call is prohibited.", "req");
            }
            return req.bindJSON(DefaultCrumbIssuer.class, formData);
        }
    }

    @Extension
    @Restricted(NoExternalUse.class)
    public static class ExcludeSessionIdAdministrativeMonitor extends AdministrativeMonitor {

        @Override
        public boolean isActivated() {
            final CrumbIssuer crumbIssuer = Jenkins.get().getCrumbIssuer();
            if (crumbIssuer instanceof DefaultCrumbIssuer) {
                return EXCLUDE_SESSION_ID;
            }
            return false;
        }

        @Override
        public boolean isSecurity() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Warn when session ID is excluded from Default Crumb Issuer"; // TODO i18n
        }

        @POST
        public void doAct(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String learn, @QueryParameter String dismiss) throws IOException, ServletException {
            if (learn != null) {
                rsp.sendRedirect("https://www.jenkins.io/redirect/csrf-protection/");
                return;
            }
            if (dismiss != null) {
                disable(true);
            }
            rsp.forwardToPreviousPage(req);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(DefaultCrumbIssuer.class.getName());
}
