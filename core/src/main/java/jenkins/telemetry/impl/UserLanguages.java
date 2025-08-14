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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import jenkins.telemetry.Telemetry;
import jenkins.util.HttpServletFilter;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class UserLanguages extends Telemetry {

    private static final Map<String, AtomicLong> requestsByLanguage = new ConcurrentSkipListMap<>();

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
        if (requestsByLanguage.isEmpty()) {
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

    @Extension
    public static final class AcceptLanguageFilter implements HttpServletFilter {

        @Override
        public boolean handle(HttpServletRequest req, HttpServletResponse rsp) throws IOException, ServletException {
            if (!Telemetry.isDisabled()) {
                String language = req.getHeader("Accept-Language");
                if (language != null) {
                    if (!requestsByLanguage.containsKey(language)) {
                        requestsByLanguage.put(language, new AtomicLong(0));
                    }
                    requestsByLanguage.get(language).incrementAndGet();
                }
            }
            return false;
        }

    }
}
