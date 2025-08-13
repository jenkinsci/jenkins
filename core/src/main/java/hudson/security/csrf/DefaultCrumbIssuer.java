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
import hudson.model.ModelObject;
import hudson.model.PersistentDescriptor;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
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
import org.kohsuke.stapler.StaplerRequest2;
import org.springframework.security.core.Authentication;

/**
 * A crumb issuing algorithm based on the request principal and the remote address.
 *
 * @author dty
 */
public class DefaultCrumbIssuer extends CrumbIssuer {

    private transient MessageDigest md;
    private boolean excludeClientIPFromCrumb;

    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* non-final: Groovy Console */ boolean EXCLUDE_SESSION_ID = SystemProperties.getBoolean(DefaultCrumbIssuer.class.getName() + ".EXCLUDE_SESSION_ID");

    @DataBoundConstructor
    public DefaultCrumbIssuer(boolean excludeClientIPFromCrumb) {
        this.excludeClientIPFromCrumb = excludeClientIPFromCrumb;
        initializeMessageDigest();
    }

    public boolean isExcludeClientIPFromCrumb() {
        return this.excludeClientIPFromCrumb;
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
        if (request instanceof HttpServletRequest) {
            if (md != null) {
                HttpServletRequest req = (HttpServletRequest) request;
                StringBuilder buffer = new StringBuilder();
                Authentication a = Jenkins.getAuthentication2();
                buffer.append(a.getName());
                buffer.append(';');
                if (!isExcludeClientIPFromCrumb()) {
                    buffer.append(getClientIP(req));
                }
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

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private String getClientIP(HttpServletRequest req) {
        String defaultAddress = req.getRemoteAddr();
        String forwarded = req.getHeader(X_FORWARDED_FOR);
        if (forwarded != null) {
            String[] hopList = forwarded.split(",");
            if (hopList.length >= 1) {
                return hopList[0];
            }
        }
        return defaultAddress;
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

    private static final Logger LOGGER = Logger.getLogger(DefaultCrumbIssuer.class.getName());
}
