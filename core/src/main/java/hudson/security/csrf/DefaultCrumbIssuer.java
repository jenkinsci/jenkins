/**
 * Copyright (c) 2008-2009 Yahoo! Inc. 
 * All rights reserved. 
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.security.csrf;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.Hudson;
import hudson.model.ModelObject;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A crumb issuing algorithm based on the request principal and the remote address.
 * 
 * @author dty
 *
 */
public class DefaultCrumbIssuer extends CrumbIssuer {

    private MessageDigest md;

    DefaultCrumbIssuer() {
        try {
            this.md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            this.md = null;
            LOGGER.log(Level.SEVERE, "Can't find MD5", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String issueCrumb(ServletRequest request, String salt) {
        if (request instanceof HttpServletRequest) {
            if (md != null) {
                HttpServletRequest req = (HttpServletRequest) request;
                StringBuilder buffer = new StringBuilder();
                Authentication a = Hudson.getAuthentication();
                if (a != null) {
                    buffer.append(a.getName());
                }
                buffer.append(';');
                buffer.append(req.getRemoteAddr());

                md.update(buffer.toString().getBytes());
                byte[] crumbBytes = md.digest(salt.getBytes());

                StringBuilder hexString = new StringBuilder();
                for (int i = 0; i < crumbBytes.length; i++) {
                    String hex = Integer.toHexString(0xFF & crumbBytes[i]);
                    if (hex.length() == 1) {
                        hexString.append('0');
                    }
                    hexString.append(hex);
                }
                return hexString.toString();
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validateCrumb(ServletRequest request, String salt, String crumb) {
        if (request instanceof HttpServletRequest) {
            String newCrumb = issueCrumb(request, salt);
            if ((newCrumb != null) && (crumb != null)) {
                return newCrumb.equals(crumb);
            }
        }
        return false;
    }

    @Extension
    public static final class DescriptorImpl extends CrumbIssuerDescriptor<DefaultCrumbIssuer> implements ModelObject {

        public DescriptorImpl() {
            super(Hudson.getInstance().getSecretKey(), System.getProperty("hudson.security.csrf.requestfield", ".crumb"));
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.DefaultCrumbIssuer_DisplayName();
        }

        public DefaultCrumbIssuer newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new DefaultCrumbIssuer();
        }
    }
    
    private static final Logger LOGGER = Logger.getLogger(DefaultCrumbIssuer.class.getName());
}
