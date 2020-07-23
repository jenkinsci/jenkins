/*
 * Copyright (c) 2008-2010 Yahoo! Inc.
 * All rights reserved. 
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.security.csrf;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.PersistentDescriptor;
import jenkins.util.SystemProperties;
import hudson.Util;
import jenkins.model.Jenkins;
import hudson.model.ModelObject;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import jenkins.security.HexStringConfidentialKey;

import net.sf.json.JSONObject;

import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
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
    public static /* non-final: Groovy Console */ boolean EXCLUDE_SESSION_ID = SystemProperties.getBoolean(DefaultCrumbIssuer.class.getName() + ".EXCLUDE_SESSION_ID");

    @DataBoundConstructor
    public DefaultCrumbIssuer(boolean excludeClientIPFromCrumb) {
        this.excludeClientIPFromCrumb = excludeClientIPFromCrumb;
        initializeMessageDigest();
    }

    public boolean isExcludeClientIPFromCrumb() {
        return this.excludeClientIPFromCrumb;
    }
    
    private Object readResolve() {
        initializeMessageDigest();
        return this;
    }

    private void initializeMessageDigest() {
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            md = null;
            LOGGER.log(Level.SEVERE, e, () -> "Cannot find SHA-256 MessageDigest implementation.");
        }
    }
    
    @Override
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

                md.update(buffer.toString().getBytes());
                return Util.toHexString(md.digest(salt.getBytes()));
            }
        }
        return null;
    }

    @Override
    public boolean validateCrumb(ServletRequest request, String salt, String crumb) {
        if (request instanceof HttpServletRequest) {
            String newCrumb = issueCrumb(request, salt);
            if ((newCrumb != null) && (crumb != null)) {
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

        private final static HexStringConfidentialKey CRUMB_SALT = new HexStringConfidentialKey(Jenkins.class,"crumbSalt",16);
        
        public DescriptorImpl() {
            super(CRUMB_SALT.get(), SystemProperties.getString("hudson.security.csrf.requestfield", CrumbIssuer.DEFAULT_CRUMB_NAME));
        }

        @Override
        public String getDisplayName() {
            return Messages.DefaultCrumbIssuer_DisplayName();
        }

        @Override
        public DefaultCrumbIssuer newInstance(StaplerRequest req, JSONObject formData) throws FormException {
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
