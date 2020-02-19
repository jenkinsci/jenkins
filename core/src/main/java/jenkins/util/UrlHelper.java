/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.util;

import jenkins.org.apache.commons.validator.routines.DomainValidator;
import jenkins.org.apache.commons.validator.routines.UrlValidator;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Objective is to validate an URL in a lenient way sufficiently strict to avoid too weird URL
 * but to still allow particular internal URL to be accepted
 */
@Restricted(NoExternalUse.class)
public class UrlHelper {
    /**
     * Authorize the {@code _} and {@code -} characters in <strong>domain</strong>
     * <p>
     * Avoid {@code -} to be first or last, and {@code .} to be first (but can be last)
     * <p>
     * 
     * Lenient version of: <ol>
     * <li> <a href="https://tools.ietf.org/html/rfc952">RFC-952</a> GRAMMATICAL HOST TABLE SPECIFICATION</li>
     * <li> <a href="https://www.ietf.org/rfc/rfc1034.txt">RFC-1034</a> 3.5</li>
     * <li> <a href="https://www.ietf.org/rfc/rfc1738.txt">RFC-1738</a>3.1, host</li>
     * <li> <a href="https://tools.ietf.org/html/rfc1123">RFC-1123</a> 2.1</li>
     * </ol>
     * <p>
     * 
     * Deliberately allow: <ol>
     * <li> short domain name (often there are rules like minimum of 3 characters)</li>
     * <li> long domain name (normally limit on whole domain of 255 and for each subdomain/label of 63)</li>
     * <li> starting by numbers (disallowed by RFC-952 and RFC-1034, but nowadays it's supported by RFC-1123)</li>
     * <li> use of underscore (not explicitly allowed in RFC but could occur in internal network, we do not speak about path here, just domain)</li>
     * <li> custom TLD like "intern" that is not standard but could be registered locally in a network</li>
     * </ol>
     */
    private static String DOMAIN_REGEX = System.getProperty(
            UrlHelper.class.getName() + ".DOMAIN_REGEX", 
            "^" + 
            "\\w" + // must start with letter / number / underscore
                "(-*(\\.|\\w))*" +// dashes are allowed but not as last character
                "\\.*" + // can end with zero (most common), one or multiple dots 
                "(:\\d{1,5})?" + // and potentially the port specification
                "$"
    );
    
    public static boolean isValidRootUrl(String url) {
        UrlValidator validator = new CustomUrlValidator();
        return validator.isValid(url);
    }
    
    private static class CustomUrlValidator extends UrlValidator {
        private CustomUrlValidator() {
            super(new String[]{"http", "https"}, UrlValidator.ALLOW_LOCAL_URLS + UrlValidator.NO_FRAGMENTS);
        }
        
        @Override 
        protected boolean isValidAuthority(String authority) {
            boolean superResult = super.isValidAuthority(authority);
            if(superResult && authority.contains("[")){
                // to support ipv6
                return true;
            }
            if(!superResult && authority == null){
                return false;
            }
            String authorityASCII = DomainValidator.unicodeToASCII(authority);
            return authorityASCII.matches(DOMAIN_REGEX);
        }
    
        @Override 
        protected boolean isValidQuery(String query) {
            // does not accept query
            return query == null;
        }
    }
}
