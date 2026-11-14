/*
 * The MIT License
 *
 * Copyright (c) 2026, Tim Jacomb
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

package jenkins.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Run;
import hudson.slaves.DumbSlave;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class NodeBuildsProviderTest {

    @Test
    void mergesProviders(JenkinsRule r) throws Exception {
        DumbSlave agent = r.createOnlineSlave();
        FreeStyleProject onAgent = r.createFreeStyleProject("onAgent");
        onAgent.setAssignedNode(agent);
        FreeStyleProject other = r.createFreeStyleProject("other");
        other.setAssignedNode(r.jenkins);
        r.buildAndAssertSuccess(other);
        r.buildAndAssertSuccess(onAgent);
        r.buildAndAssertSuccess(other);
        r.buildAndAssertSuccess(other);

        // the test provider claims the odd builds of "other" ran on the agent
        assertEquals(List.of("other #3", "onAgent #1", "other #1"), names(agent.toComputer().getBuilds()));
        assertEquals(List.of("other #3", "other #2", "other #1"), names(r.jenkins.toComputer().getBuilds()));

        JenkinsRule.WebClient wc = r.createWebClient();
        assertEquals(List.of("other #3", "onAgent #1", "other #1"), names(wc, agent.toComputer()));
        assertEquals(List.of("other #3", "other #2", "other #1"), names(wc, r.jenkins.toComputer()));
    }

    private static List<String> names(Iterable<? extends Run<?, ?>> runs) {
        List<String> names = new ArrayList<>();
        for (Run<?, ?> run : runs) {
            names.add(run.getFullDisplayName());
        }
        return names;
    }

    private static List<String> names(JenkinsRule.WebClient wc, Computer computer) throws Exception {
        JSONArray builds = wc.getJSON(computer.getUrl() + "buildList").getJSONObject().getJSONObject("data").getJSONArray("builds");
        List<String> names = new ArrayList<>();
        for (Object build : builds) {
            JSONObject json = (JSONObject) build;
            names.add(json.getString("parentFullDisplayName") + " " + json.getString("displayName"));
        }
        return names;
    }

    @TestExtension("mergesProviders")
    public static class OddBuildsOfOther extends NodeBuildsProvider {

        @NonNull
        @Override
        public Iterable<? extends Run<?, ?>> getCandidates(@NonNull Node node) {
            if (node == Jenkins.get()) {
                return List.of();
            }
            return Jenkins.get().getItemByFullName("other", FreeStyleProject.class).getBuilds();
        }

        @Override
        public boolean usedNode(@NonNull Run<?, ?> run, @NonNull Node node) {
            return run.getNumber() % 2 == 1;
        }
    }
}
