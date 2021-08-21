/*
 * The MIT License
 *
 * Copyright (c) 2018, Daniel Beck
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
package jenkins.telemetry.impl;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.PluginServletFilter;
import jenkins.telemetry.Telemetry;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
@Restricted(NoExternalUse.class)
public class UserLanguages extends Telemetry {

    private static final Map<String, AtomicLong> requestsByLanguage = new ConcurrentSkipListMap<>();
    private static Logger LOGGER = Logger.getLogger(UserLanguages.class.getName());

    @NonNull
    @Override
    public String getId() {
        return UserLanguages.class.getName();
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Browser languages";
    }

    @NonNull
    @Override
    public LocalDate getStart() {
        return LocalDate.of(2018, 10, 1);
    }

    @NonNull
    @Override
    public LocalDate getEnd() {
        return LocalDate.of(2019, 1, 1);
    }

    @Override
    public JSONObject createContent() {
        if (requestsByLanguage.size() == 0) {
            return null;
        }
        Map<String, AtomicLong> currentRequests = new TreeMap<>(requestsByLanguage);
        requestsByLanguage.clear();

        JSONObject payload = new JSONObject();
        for (Map.Entry<String, AtomicLong> entry : currentRequests.entrySet()) {
            payload.put(entry.getKey(), entry.getValue().longValue());
        }
        return payload;
    }

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)
    public static void setUpFilter() {
        Filter filter = new AcceptLanguageFilter();
        if (!PluginServletFilter.hasFilter(filter)) {
            try {
                PluginServletFilter.addFilter(filter);
            } catch (ServletException ex) {
                LOGGER.log(Level.WARNING, "Failed to set up languages servlet filter", ex);
            }
        }
    }

    public static final class AcceptLanguageFilter implements Filter {

        @Override
        public void init(FilterConfig filterConfig) {

        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            if (request instanceof HttpServletRequest && !Telemetry.isDisabled()) {
                HttpServletRequest httpServletRequest = (HttpServletRequest) request;
                String language = httpServletRequest.getHeader("Accept-Language");
                if (language != null) {
                    if (!requestsByLanguage.containsKey(language)) {
                        requestsByLanguage.put(language, new AtomicLong(0));
                    }
                    requestsByLanguage.get(language).incrementAndGet();
                }
            }
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {

        }

        @Override
        public boolean equals(Object obj) { // support PluginServletFilter#hasFilter
            return obj != null && obj.getClass() == AcceptLanguageFilter.class;
        }

        // findbugs
        @Override
        public int hashCode() {
            return 42;
        }
    }
}
