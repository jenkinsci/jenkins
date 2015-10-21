/**
 * Copyright (c) 2008-2009 Yahoo! Inc. 
 * All rights reserved. 
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package org.jvnet.hudson.test;

import javax.servlet.ServletRequest;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.ModelObject;
import hudson.security.csrf.CrumbIssuer;
import hudson.security.csrf.CrumbIssuerDescriptor;

/**
 * A crumb issuer that issues a constant crumb value. Used for unit testing.
 * @author dty
 */
public class TestCrumbIssuer extends CrumbIssuer
{
    @Override
    protected String issueCrumb( ServletRequest request, String salt )
    {
        return "test";
    }

    @Override
    public boolean validateCrumb( ServletRequest request, String salt, String crumb )
    {
        return "test".equals(crumb);
    }

    @Extension
    public static final class DescriptorImpl extends CrumbIssuerDescriptor<TestCrumbIssuer> implements ModelObject {
        public DescriptorImpl()
        {
            super(null, null);
            load();
        }

        public TestCrumbIssuer newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new TestCrumbIssuer();
        }
    }

}
