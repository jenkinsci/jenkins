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

@Restricted(NoExternalUse.class)
public class UrlHelper {
    /**
     * Authorize the {@code _} and {@code -} characters in domain
     * <p>
     * Avoid {@code -} and {@code .} and {@code -} to be first or last
     */
    private static String DOMAIN_REGEX = System.getProperty(
            UrlHelper.class.getName() + ".DOMAIN_REGEX", 
            "(\\w(\\.?-?\\w+)*)(:\\d{1,5})?"
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
            // to support ipv6
            boolean superResult = super.isValidAuthority(authority);
            if(!superResult && authority == null){
                return false;
            }
            String authorityASCII = DomainValidator.unicodeToASCII(authority);
            return authorityASCII.matches(DOMAIN_REGEX);
        }
    }
}
