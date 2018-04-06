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

import jenkins.org.apache.commons.validator.routines.UrlValidator;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.net.MalformedURLException;
import java.net.URL;

@Restricted(NoExternalUse.class)
public class UrlHelper {
    public static boolean isValidRootUrl(String url) {
        String[] schemes = {"http", "https"};
        // option to accept url like http://localhost or http://SERVER_JENKINS that are often used inside company's network
        UrlValidator validator = new UrlValidator(schemes, UrlValidator.ALLOW_LOCAL_URLS + UrlValidator.NO_FRAGMENTS);
        boolean isValid = validator.isValid(url);
        if (!isValid) {
            // potentially it contains _'s in hostname which seems accepted but not by the UrlValidator
            // https://issues.apache.org/jira/browse/VALIDATOR-358
            try {
                URL urlObject = new URL(url);
                String host = urlObject.getHost();
                if (host.contains("_")) {
                    String hostWithoutUnderscore = host.replace("_", "");
                    String modifiedUrl = url.replace(host, hostWithoutUnderscore);
                    isValid = validator.isValid(modifiedUrl);
                }
            } catch (MalformedURLException e) {
                return false;
            }
        }
        
        return isValid;
    }
}
