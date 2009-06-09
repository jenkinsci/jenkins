/**
 * Copyright (c) 2008-2009 Yahoo! Inc. 
 * All rights reserved. 
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.security.csrf;

import org.kohsuke.stapler.StaplerRequest;

import hudson.Util;
import hudson.model.Descriptor;

/**
 * Describes global configuration for crumb issuers. Create subclasses to specify
 * additional global configuration for custom crumb issuers.
 * 
 * @author dty
 *
 */
public abstract class CrumbIssuerDescriptor<T extends CrumbIssuer> extends Descriptor<CrumbIssuer> {

    private String crumbSalt;
    private String crumbRequestField;

    /**
     * Crumb issuers always take a salt and a request field name.
     *
     * @param salt Salt value
     * @param crumbRequestField Request parameter name containing crumb from previous response
     */
    protected CrumbIssuerDescriptor(String salt, String crumbRequestField) {
        setCrumbSalt(salt);
        setCrumbRequestField(crumbRequestField);
    }

    /**
     * Get the salt value.
     * @return
     */
    public String getCrumbSalt() {
        return crumbSalt;
    }

    /**
     * Set the salt value. Must not be null.
     * @param salt
     */
    public void setCrumbSalt(String salt) {
        if (Util.fixEmptyAndTrim(salt) == null) {
            crumbSalt = "hudson.crumb";
        } else {
            crumbSalt = salt;
        }
    }

    /**
     * Gets the request parameter name that contains the crumb generated from a
     * previous response.
     *
     * @return
     */
    public String getCrumbRequestField() {
        return crumbRequestField;
    }

    /**
     * Set the request parameter name. Must not be null.
     *
     * @param requestField
     */
    public void setCrumbRequestField(String requestField) {
        if (Util.fixEmptyAndTrim(requestField) == null) {
            crumbRequestField = ".crumb";
        } else {
            crumbRequestField = requestField;
        }
    }

    @Override
    public boolean configure(StaplerRequest request) {
        setCrumbSalt(request.getParameter("csrf_crumbSalt"));
        setCrumbRequestField(request.getParameter("csrf_crumbRequestField"));
        save();

        return true;
    }

    /**
     * Returns the Jelly script that contains common configuration.
     */
    @Override
    public final String getConfigPage() {
        return getViewPage(CrumbIssuer.class, "config.jelly");
    }

    /**
     * Returns a subclass specific configuration page. The base CrumbIssuerDescriptor
     * class provides configuration options that are common to all crumb issuers.
     * Implementations may provide additional configuration options which are
     * kept in Jelly script file tied to the subclass.
     * <p>
     * By default, an empty string is returned, which signifies no additional
     * configuration is needed for a crumb issuer. Override this method if your
     * crumb issuer has additional configuration options.
     * <p>
     * A typical implementation of this method would look like:
     * <p>
     * <code>
     * return getViewPage(clazz, "config.jelly");
     * </code>
     *
     * @return An empty string, signifying no additional configuration.
     */
    public String getSubConfigPage() {
        return "";
    }
}
