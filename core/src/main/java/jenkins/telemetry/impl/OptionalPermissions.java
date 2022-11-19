/*
 * The MIT License
 *
 * Copyright (c) 2022, CloudBees, Inc.
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
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Run;
import hudson.security.Permission;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import jenkins.model.Jenkins;
import jenkins.telemetry.Telemetry;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Telemetry implementation that gathers information about optional permissions.
 */
@Extension
@Restricted(NoExternalUse.class)
public class OptionalPermissions extends Telemetry {
    private static final Set<String> OPTIONAL_PERMISSION_IDS = Set.of(
            // Defined in core
            Computer.EXTENDED_READ.getId(),
            Item.EXTENDED_READ.getId(),
            Item.WIPEOUT.getId(),
            Jenkins.MANAGE.getId(),
            Jenkins.SYSTEM_READ.getId(),
            Run.ARTIFACTS.getId(),
            // Defined in credentials
            "com.cloudbees.plugins.credentials.CredentialsProvider.UseOwn",
            "com.cloudbees.plugins.credentials.CredentialsProvider.UseItem");

    @Override
    public String getDisplayName() {
        return "Activation of permissions that are not enabled by default";
    }

    @Override
    public LocalDate getStart() {
        return LocalDate.of(2022, 11, 1);
    }

    @Override
    public LocalDate getEnd() {
        return LocalDate.of(2023, 3, 1);
    }

    @Override
    public JSONObject createContent() {
        Map<String, Boolean> permissions = new TreeMap<>();
        for (Permission p : Permission.getAll()) {
            if (OPTIONAL_PERMISSION_IDS.contains(p.getId())) {
                permissions.put(p.getId(), p.getEnabled());
            }
        }
        JSONObject payload = new JSONObject();
        payload.put("components", buildComponentInformation());
        payload.put("permissions", permissions);
        return payload;
    }

}
