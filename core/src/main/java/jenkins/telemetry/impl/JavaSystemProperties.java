/*
 * The MIT License
 *
 * Copyright (c) 2023, Daniel Beck
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
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import jenkins.telemetry.Telemetry;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Collect the value of various Java system properties describing the environment.
 */
@Extension
@Restricted(NoExternalUse.class)
public class JavaSystemProperties extends Telemetry {
    private static final String[] PROPERTIES = new String[] {
            "file.encoding",
            "file.separator",
            "java.vm.name",
            "java.vm.vendor",
            "java.vm.version",
            "os.arch",
            "os.name",
            "os.version",
            "user.language",
    };

    public Map<String, String> getProperties() {
        Map<String, String> properties = new TreeMap<>();
        for (String property : PROPERTIES) {
            final String value = System.getProperty(property);
            properties.put(property, value);
        }
        return properties;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "System Properties";
    }

    @NonNull
    @Override
    public LocalDate getStart() {
        return LocalDate.of(2026, 1, 4);
    }

    @NonNull
    @Override
    public LocalDate getEnd() {
        return LocalDate.of(2026, 4, 1);
    }

    @Override
    public JSONObject createContent() {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, String> entry : getProperties().entrySet()) {
            final String value = entry.getValue();
            o = o.element(entry.getKey(), value == null ? "(undefined)" : value);
        }
        o.put("components", buildComponentInformation());
        return o;
    }
}
