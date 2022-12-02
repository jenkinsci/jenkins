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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Node;
import hudson.slaves.Cloud;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import jenkins.diagnostics.ControllerExecutorsAgents;
import jenkins.diagnostics.ControllerExecutorsNoAgents;
import jenkins.model.Jenkins;
import jenkins.telemetry.Telemetry;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
@Extension
public class DistributedBuilds extends Telemetry {
    @NonNull
    @Override
    public String getDisplayName() {
        return "Distributed Builds";
    }

    @NonNull
    @Override
    public LocalDate getStart() {
        return LocalDate.of(2022, 12, 1);
    }

    @NonNull
    @Override
    public LocalDate getEnd() {
        return LocalDate.of(2023, 3, 1);
    }

    @Override
    public JSONObject createContent() {
        JSONObject payload = new JSONObject();

        payload.put("controllerExecutors", Jenkins.get().getNumExecutors());

        // Capture whether admin monitors are dismissed
        payload.put("controllerExecutorsWithAgentsWarning", ExtensionList.lookupSingleton(ControllerExecutorsAgents.class).isEnabled());
        payload.put("controllerExecutorsWithoutAgentsWarning", ExtensionList.lookupSingleton(ControllerExecutorsNoAgents.class).isEnabled());

        Map<String, Integer> clouds = new HashMap<>();
        for (Cloud cloud : Jenkins.get().clouds) {
            clouds.compute(cloud.getClass().getName(), (key, value) -> value == null ? 1 : value + 1);
        }
        payload.put("clouds", clouds);

        Map<String, Integer> agents = new HashMap<>();
        for (Node agent : Jenkins.get().getNodes()) {
            agents.compute(agent.getClass().getName(), (key, value) -> value == null ? 1 : value + 1);
        }
        payload.put("agents", agents);

        // Try to understand the complexity of the instance
        payload.put("items", Jenkins.get().getAllItems().size());

        payload.put("components", buildComponentInformation());
        return payload;
    }
}
