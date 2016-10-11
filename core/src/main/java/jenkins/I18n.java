/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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
package jenkins;

import hudson.Extension;
import hudson.model.RootAction;
import hudson.util.HttpResponses;
import jenkins.util.ResourceBundleUtil;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.MissingResourceException;

/**
 * Internationalization REST (ish) API.
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 * @since 2.0
 */
@Extension
@Restricted(NoExternalUse.class)
public class I18n implements RootAction {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconFileName() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUrlName() {
        return "i18n";
    }

    /**
     * Get a localised resource bundle.
     * <p>
     * URL: {@code i18n/resourceBundle}.
     * <p>
     * Parameters:
     * <ul>
     *     <li>{@code baseName}: The resource bundle base name.</li>
     *     <li>{@code language}: {@link Locale} Language. (optional)</li>
     *     <li>{@code country}: {@link Locale} Country. (optional)</li>
     *     <li>{@code variant}: {@link Locale} Language variant. (optional)</li>
     * </ul>
     *
     * @param request The request.
     * @return The JSON response.
     */
    public HttpResponse doResourceBundle(StaplerRequest request) {
        String baseName = request.getParameter("baseName");

        if (baseName == null) {
            return HttpResponses.errorJSON("Mandatory parameter 'baseName' not specified.");
        }

        String language = request.getParameter("language");
        String country = request.getParameter("country");
        String variant = request.getParameter("variant");

        try {
            Locale locale = request.getLocale();

            if (language != null && country != null && variant != null) {
                locale = new Locale(language, country, variant);
            } else if (language != null && country != null) {
                locale = new Locale(language, country);
            } else if (language != null) {
                locale = new Locale(language);
            }

            return HttpResponses.okJSON(ResourceBundleUtil.getBundle(baseName, locale));
        } catch (MissingResourceException e) {
            return HttpResponses.errorJSON(e.getMessage(), HttpServletResponse.SC_NOT_FOUND);
        } catch (Exception e) {
            return HttpResponses.errorJSON(e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
