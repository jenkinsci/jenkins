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
package jenkins.telemetry.impl;

import hudson.Extension;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import jenkins.telemetry.Telemetry;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * Telemetry implementation gathering information about system properties.
 */
@Extension
@Restricted(NoExternalUse.class)
public class SecuritySystemProperties extends Telemetry {
    @NonNull
    @Override
    public String getId() {
        return "security-system-properties";
    }

    @NonNull
    @Override
    public LocalDate getStart() {
        return LocalDate.of(2018, 9, 1);
    }

    @NonNull
    @Override
    public LocalDate getEnd() {
        return LocalDate.of(2018, 12, 1);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Use of Security-related Java system properties";
    }

    @NonNull
    @Override
    public JSONObject createContent() {
        Map<String, String> security = new TreeMap<>();
        putBoolean(security, "hudson.ConsoleNote.INSECURE", false);
        putBoolean(security, "hudson.logging.LogRecorderManager.skipPermissionCheck", false);
        putBoolean(security, "hudson.model.AbstractItem.skipPermissionCheck", false);
        putBoolean(security, "hudson.model.ParametersAction.keepUndefinedParameters", false);
        putBoolean(security, "hudson.model.Run.skipPermissionCheck", false);
        putBoolean(security, "hudson.model.UpdateCenter.skipPermissionCheck", false);
        putBoolean(security, "hudson.model.User.allowNonExistentUserToLogin", false);
        putBoolean(security, "hudson.model.User.allowUserCreationViaUrl", false);
        putBoolean(security, "hudson.model.User.SECURITY_243_FULL_DEFENSE", true);
        putBoolean(security, "hudson.model.User.skipPermissionCheck", false);
        putBoolean(security, "hudson.PluginManager.skipPermissionCheck", false);
        putBoolean(security, "hudson.remoting.URLDeserializationHelper.avoidUrlWrapping", false);
        putBoolean(security, "hudson.search.Search.skipPermissionCheck", false);
        putBoolean(security, "jenkins.security.ClassFilterImpl.SUPPRESS_WHITELIST", false);
        putBoolean(security, "jenkins.security.ClassFilterImpl.SUPPRESS_ALL", false);
        putBoolean(security, "org.kohsuke.stapler.Facet.allowViewNamePathTraversal", false);
        putBoolean(security, "org.kohsuke.stapler.jelly.CustomJellyContext.escapeByDefault", true);

        putStringInfo(security, "hudson.model.ParametersAction.safeParameters");
        putStringInfo(security, "hudson.model.DirectoryBrowserSupport.CSP");
        putStringInfo(security, "hudson.security.HudsonPrivateSecurityRealm.ID_REGEX");

        Map<String, Object> info = new TreeMap<>();
        VersionNumber jenkinsVersion = Jenkins.getVersion();
        info.put("core", jenkinsVersion != null ? jenkinsVersion.toString() : "UNKNOWN");
        info.put("clientDate", clientDateString());
        info.put("properties", security);

        return JSONObject.fromObject(info);
    }

    private static String clientDateString() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(tz); // strip timezone
        return df.format(new Date());
    }

    private static void putBoolean(Map<String, String> propertiesMap, String systemProperty, boolean defaultValue) {
        propertiesMap.put(systemProperty, Boolean.toString(SystemProperties.getBoolean(systemProperty, defaultValue)));
    }

    private static void putStringInfo(Map<String, String> propertiesMap, String systemProperty) {
        String reportedValue = "null";
        String value = SystemProperties.getString(systemProperty);
        if (value != null) {
            reportedValue = Integer.toString(value.length());
        }
        propertiesMap.put(systemProperty, reportedValue);
    }
}
