/*
 * The MIT License
 *
 * Copyright (c) 2025, Ahmed Anwar
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

package jenkins.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.core.Authentication;

/**
 * Tests for {@link ListNodesCommand}.
 */
@WithJenkins
public class ListNodesCommandTest {

    private CLICommand listNodesCommand;
    private CLICommandInvoker command;
    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        listNodesCommand = new ListNodesCommand();
        command = new CLICommandInvoker(j, listNodesCommand);
    }

    @Nested
    class BasicTests {
        @Test
        void shouldListSingleNode() throws Exception {
            DumbSlave node = j.createOnlineSlave();

            CLICommandInvoker.Result result = command.invoke();

            assertThat(result.stdout(), containsString("Node: " + node.getDisplayName()));
            assertThat(result.returnCode(), is(0));
        }
    }

    @Nested
    class StatusTests {
        @Test
        void shouldListOnlineNodeWhenStatusIsOnline() throws Exception {
            DumbSlave onlineNode = j.createOnlineSlave();

            CLICommandInvoker.Result result = command.invokeWithArgs("-status", "ONLINE");

            assertThat(result.stdout(), containsString(onlineNode.getNodeName()));
            assertThat(result.stderr(), is(emptyString()));
            assertThat(result.returnCode(), is(0));
        }

        @Test
        void shouldListOfflineNodeWhenStatusIsOffline() throws Exception {
            DumbSlave offlineNode = j.createOnlineSlave();
            offlineNode.toComputer().setTemporaryOfflineCause(new OfflineCause.ByCLI("Testing offline node"));

            CLICommandInvoker.Result result = command.invokeWithArgs("-status", "OFFLINE");

            assertThat(result.stdout(), containsString(offlineNode.getNodeName()));
            assertThat(result.stderr(), is(emptyString()));
            assertThat(result.returnCode(), is(0));
        }

        @Test
        void shouldListBothOnlineAndOfflineNodesWhenStatusIsAll() throws Exception {
            // Create online node
            DumbSlave onlineNode = j.createOnlineSlave();
            String onlineNodeName = onlineNode.getNodeName();

            // Create offline node
            DumbSlave offlineNode = j.createOnlineSlave();
            offlineNode.toComputer().setTemporaryOfflineCause(new OfflineCause.ByCLI("Testing offline node"));
            String offlineNodeName = offlineNode.getNodeName();

            // Invoke command with -status ALL
            CLICommandInvoker.Result result = command.invokeWithArgs("-status", "ALL");

            // Verify both nodes are listed
            assertThat(result.stdout(), containsString(onlineNodeName));
            assertThat(result.stdout(), containsString(offlineNodeName));
            assertThat(result.stderr(), is(emptyString()));
            assertThat(result.returnCode(), is(0));
        }
    }

    @Nested
    class ControllerTests {
        @Test
        void shouldExcludeControllerNodeWhenSpecified() throws Exception {
            Jenkins controller = j.jenkins;

            CLICommandInvoker.Result result = command.invokeWithArgs("-exclude-controller");

            // Controller node should not be listed
            assertThat(result.stdout(), not(containsString(controller.getDisplayName())));
            assertThat(result.stderr(), is(emptyString()));
            assertThat(result.returnCode(), is(0));
        }
    }

    @Nested
    class LabelTests {
        @Test
        void shouldFilterNodesBySingleLabel() throws Exception {
            DumbSlave labeledNode = j.createOnlineSlave();
            labeledNode.setLabelString("my-label");

            CLICommandInvoker.Result result = command.invokeWithArgs("-labels", "my-label");

            assertThat(result.stdout(), containsString(labeledNode.getNodeName()));
            assertThat(result.stderr(), is(emptyString()));
            assertThat(result.returnCode(), is(0));
        }

        @Test
        void shouldFilterNodesByMultipleLabels() throws Exception {
            DumbSlave labeledNode = j.createOnlineSlave();
            labeledNode.setLabelString("my-label another-label");

            CLICommandInvoker.Result result = command.invokeWithArgs("-labels", "my-label,another-label");

            assertThat(result.stdout(), containsString(labeledNode.getNodeName()));
            assertThat(result.stderr(), is(emptyString()));
            assertThat(result.returnCode(), is(0));
        }

        @Test
        public void shouldShowNoNodesWhenLabelFilterDoesNotMatch() throws Exception {
            // Create an online node with label "existing-label"
            DumbSlave onlineNode = j.createOnlineSlave();
            onlineNode.setLabelString("existing-label");

            // Invoke command with a label that doesn't exist
            CLICommandInvoker.Result result = command.invokeWithArgs("-labels", "nonexistent-label");

            assertThat(result.stdout(), containsString("No nodes found"));
            assertThat(result.stderr(), is(emptyString()));
            assertThat(result.returnCode(), is(0));
        }

        @Test
        void shouldHandleEmptyLabels() throws Exception {
            DumbSlave node = j.createOnlineSlave();
            node.setLabelString("test-label");

            CLICommandInvoker.Result result = command.invokeWithArgs("-labels", "");

            assertThat(result.stdout(), containsString(node.getNodeName())); // Empty labels should match all nodes
            assertThat(result.stderr(), is(emptyString()));
            assertThat(result.returnCode(), is(0));
        }

        @Test
        void shouldRejectLabelInputThatIsTooLong() {
            String longLabel = "a".repeat(1001); // exceeds MAX_LABELS_LENGTH

            CLICommandInvoker.Result result = command.invokeWithArgs("-labels", longLabel);

            assertThat(result.stderr(), containsString("exceeds maximum length"));
            assertThat(result.returnCode(), is(1));
        }

        // label mode tests

        @Test
        void shouldMatchNodeWhenAnyLabelMatches() throws Exception {
            DumbSlave node = j.createOnlineSlave();
            node.setLabelString("linux docker");

            CLICommandInvoker.Result result = command.invokeWithArgs("-labels", "docker,gpu", "-labels-mode", "ANY");

            assertThat(result.stdout(), containsString(node.getNodeName()));
            assertThat(result.stderr(), is(emptyString()));
            assertThat(result.returnCode(), is(0));
        }

        @Test
        void shouldNotMatchNodeWhenAnyLabelDoesNotMatch() throws Exception {
            DumbSlave node = j.createOnlineSlave();
            node.setLabelString("windows");

            CLICommandInvoker.Result result = command.invokeWithArgs("-labels", "gpu,mac", "-labels-mode", "ANY");

            assertThat(result.stdout(), not(containsString(node.getNodeName())));
            assertThat(result.returnCode(), is(0));
        }

        @Test
        void shouldMatchNodeWhenAllLabelsMatch() throws Exception {
            DumbSlave node = j.createOnlineSlave();
            node.setLabelString("linux docker");

            CLICommandInvoker.Result result = command.invokeWithArgs("-labels", "linux,docker", "-labels-mode", "ALL");

            assertThat(result.stdout(), containsString(node.getNodeName()));
            assertThat(result.stderr(), is(emptyString()));
            assertThat(result.returnCode(), is(0));
        }

        @Test
        void shouldNotMatchNodeWhenAllLabelsDoNotMatch() throws Exception {
            DumbSlave node = j.createOnlineSlave();
            node.setLabelString("linux docker");

            CLICommandInvoker.Result result = command.invokeWithArgs("-labels", "linux,gpu", "-labels-mode", "ALL");

            assertThat(result.stdout(), not(containsString(node.getNodeName())));
            assertThat(result.returnCode(), is(0));
        }
    }

    @Nested
    class ExecutorTests {
        @Test
        void shouldFilterByMinExecutors() throws Exception {
            // Node that matches min-executors filter
            DumbSlave matchingNode = j.createOnlineSlave();
            matchingNode.setNumExecutors(5);
            j.jenkins.addNode(matchingNode); // Re-add to apply changes
            j.configRoundtrip(matchingNode);
            j.waitOnline(matchingNode);

            // Node that does NOT match min-executors filter
            DumbSlave nonMatchingNode = j.createOnlineSlave();
            nonMatchingNode.setNumExecutors(2);
            j.jenkins.addNode(nonMatchingNode); // Re-add to apply changes
            j.configRoundtrip(nonMatchingNode);
            j.waitOnline(nonMatchingNode);

            j.jenkins.save(); // Save Jenkins configuration

            CLICommandInvoker.Result result = command.invokeWithArgs("-min-executors", "4");

            // Should contain matching node
            assertThat(result.stdout(), containsString(matchingNode.getNodeName()));

            // Should NOT contain non-matching node
            assertThat(result.stdout(), not(containsString(nonMatchingNode.getNodeName())));

            assertThat(result.stderr(), is(emptyString()));
            assertThat(result.returnCode(), is(0));
        }

        @Test
        void shouldRejectNegativeMinExecutors() {
            CLICommandInvoker.Result result = command.invokeWithArgs("-min-executors", "-1");

            assertThat(result.stderr(), containsString("Invalid input"));
            assertThat(result.returnCode(), is(1));
        }
    }

    @Nested
    class LimitTests {
        @Test
        void shouldLimitNumberOfResults() throws Exception {
            j.createOnlineSlave();
            j.createOnlineSlave();

            CLICommandInvoker.Result result = command.invokeWithArgs("-limit", "1");

            // Only one node should appear in the output
            long count = result.stdout().lines().filter(line -> line.contains("Node:")).count();
            assertThat(count, is(1L));
        }

        @Test
        void shouldRejectNonPositiveLimit() {
            CLICommandInvoker.Result result = command.invokeWithArgs("-limit", "0");

            assertThat(result.stderr(), containsString("Invalid input"));
            assertThat(result.returnCode(), is(1));
        }
    }

    @Nested
    class CountOnlyTests {
        @Test
        void shouldOutputCountOnly() throws Exception {
            j.createOnlineSlave();
            j.createOnlineSlave();

            CLICommandInvoker.Result result = command.invokeWithArgs("-count-only");

            assertThat(result.stdout().trim(), is("3")); // 1 master (controller) + 2 agents
            assertThat(result.returnCode(), is(0));
        }

        @Test
        void shouldOutputCountOnlyWithoutController() throws Exception {
            j.createOnlineSlave();
            j.createOnlineSlave();

            CLICommandInvoker.Result result = command.invokeWithArgs("-exclude-controller", "-count-only"); // Exclude the controller (master)

            assertThat(result.stdout().trim(), is("2"));
            assertThat(result.returnCode(), is(0));
        }
    }

    @Nested
    class NameOnlyTests {
        @Test
        void shouldOutputNameOnly() throws Exception {
            DumbSlave node = j.createOnlineSlave();

            CLICommandInvoker.Result result = command.invokeWithArgs("-exclude-controller", "-name-only"); // Exclude the controller (master)

            assertThat(result.stdout().trim(), is(node.getNodeName()));
            assertThat(result.returnCode(), is(0));
        }
    }

    @Nested
    class VerboseTests {
        @Test
        void shouldShowVerboseDetailsForOnlineNode() throws Exception {
            DumbSlave node = j.createOnlineSlave();
            node.setLabelString("test-node");

            CLICommandInvoker.Result result = command.invokeWithArgs("-verbose", "-labels", "test-node");

            String output = result.stdout();

            // Basic checks
            assertThat(output, containsString(node.getNodeName()));
            assertThat(output, containsString("OS:"));
            assertThat(output, containsString("Architecture:"));
            assertThat(output, containsString("Java Version:"));
            assertThat(output, containsString("Connected Since:"));
            assertThat(output, containsString("Clock Difference:"));

            assertThat(result.returnCode(), is(0));
        }

        @Test
        void shouldShowVerboseDetailsForOfflineNode() throws Exception {
            DumbSlave node = j.createOnlineSlave();
            node.setLabelString("test-node");

            // Make node offline
            node.toComputer().setTemporaryOfflineCause(new OfflineCause.ByCLI("Testing offline node"));

            CLICommandInvoker.Result result = command.invokeWithArgs("-verbose", "-labels", "test-node");

            String output = result.stdout();

            // Basic checks
            assertThat(output, containsString(node.getNodeName()));
            assertThat(output, containsString("Offline Reason:")); // offlineCauseReason shown
            assertThat(output, containsString("Connected Since: N/A")); // should say N/A
            assertThat(output, containsString("Clock Difference: N/A")); // should say N/A

            assertThat(result.returnCode(), is(0));
        }
    }

    @Nested
    class CombinationTests {
        @Test
        void shouldCombineFiltersCorrectly() throws Exception {
            DumbSlave node = j.createOnlineSlave();
            node.setLabelString("combo-label");
            node.setNumExecutors(3);

            j.jenkins.addNode(node); // Re-add to apply changes
            j.configRoundtrip(node);
            j.waitOnline(node);

            j.jenkins.save(); // Save Jenkins configuration

            CLICommandInvoker.Result result = command.invokeWithArgs("-status", "ONLINE", "-labels", "combo-label", "-min-executors", "2");

            assertThat(result.stdout(), containsString(node.getNodeName()));
            assertThat(result.returnCode(), is(0));
        }

        @Test
        void shouldRespectMinExecutorsAndLimitTogether() throws Exception {
            // Create 3 nodes with different executors
            DumbSlave node1 = j.createOnlineSlave();
            node1.setNumExecutors(5);
            j.jenkins.addNode(node1);
            j.configRoundtrip(node1);
            j.waitOnline(node1);

            DumbSlave node2 = j.createOnlineSlave();
            node2.setNumExecutors(4);
            j.jenkins.addNode(node2);
            j.configRoundtrip(node2);
            j.waitOnline(node2);

            DumbSlave node3 = j.createOnlineSlave();
            node3.setNumExecutors(2);
            j.jenkins.addNode(node3);
            j.configRoundtrip(node3);
            j.waitOnline(node3);

            j.jenkins.save();

            CLICommandInvoker.Result result = command.invokeWithArgs("-min-executors", "3", "-limit", "1");

            String output = result.stdout();

            boolean containsNode1 = output.contains(node1.getNodeName());
            boolean containsNode2 = output.contains(node2.getNodeName());
            boolean containsNode3 = output.contains(node3.getNodeName()); // should not appear

            int countMatchingNodes = 0;
            if (containsNode1) countMatchingNodes++;
            if (containsNode2) countMatchingNodes++;
            if (containsNode3) countMatchingNodes++; // this must stay zero!

            assertThat("Only one matching node should appear", countMatchingNodes, is(1));
            assertThat("Node with 2 executors must NOT appear", containsNode3, is(false));
            assertThat(result.returnCode(), is(0));
        }
    }

    @Nested
    class PermissionTests {
        // Custom AuthorizationStrategy to grant Computer.EXTENDED_READ only for nodeWithPermission
        private AuthorizationStrategy authorizationWithExtendedReadForNode(String nodeWithPermissionName) {
            return new AuthorizationStrategy() {
                private final Set<String> permittedComputers = new HashSet<>(Collections.singletonList(nodeWithPermissionName));

                @Override
                public ACL getRootACL() {
                    return new ACL() {
                        @Override
                        public boolean hasPermission2(Authentication a, Permission permission) {
                            if (a.getName().equals("user") && permission == Jenkins.READ) {
                                return true;
                            }
                            return false;
                        }
                    };
                }

                @Override
                public ACL getACL(Computer c) {
                    return new ACL() {
                        @Override
                        public boolean hasPermission2(Authentication a, Permission permission) {
                            if (a.getName().equals("user")) {
                                if (permission == Jenkins.READ) {
                                    return true;
                                }
                                if (permission == Computer.EXTENDED_READ && permittedComputers.contains(c.getName())) {
                                    return true;
                                }
                            }
                            return false;
                        }
                    };
                }

                @Override
                public ACL getACL(Node node) {
                    return getACL(node.toComputer());
                }

                @Override
                public Set<String> getGroups() {
                    return Collections.emptySet();
                }
            };
        }

        @Test
        void shouldRunCommandWhenUserHasJenkinsReadPermission() throws Exception {
            // Set up a security realm with a user "user"
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().to("user"));

            CLICommandInvoker.Result result = command.asUser("user").invoke();

            // Expect normal behavior (not a permission error)
            assertThat(result.stderr(), not(containsString("permission")));
            assertThat(result.returnCode(), is(0));
        }

        @Test
        void shouldFailWhenUserLacksJenkinsReadPermission() throws Exception {
            // Set up a security realm with a user "user"
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant().everywhere().to("user")); // No permissions granted to "user"

            CLICommandInvoker.Result result = command.asUser("user").invoke();

            // Expect permission error
            assertThat(result.stderr(), containsString("user is missing the Overall/Read permission"));
            assertThat(result.returnCode(), is(6));
        }

        @Test
        void shouldShowVerboseDetailsWhenUserHasExtendedReadPermission() throws Exception {
            // Set up a security realm with a user "user"
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("user")
                .grant(Computer.EXTENDED_READ).everywhere().to("user")); // Grant both Jenkins.READ and Computer.EXTENDED_READ

            // Create an online node
            DumbSlave node = j.createOnlineSlave();
            node.setLabelString("test-node");
            String nodeName = node.getNodeName();

            CLICommandInvoker.Result result = command.asUser("user").invokeWithArgs("-verbose", "-labels", "test-node");

            // Verify the command runs successfully
            assertThat(result.returnCode(), is(0));
            assertThat(result.stderr(), is(emptyString())); // No errors in stderr
            assertThat(result.stdout(), containsString(nodeName)); // Node should be listed
            assertThat(result.stdout(), containsString("OS:"));
            assertThat(result.stdout(), containsString("Architecture:"));
            assertThat(result.stdout(), containsString("Java Version:"));
            assertThat(result.stdout(), containsString("Connected Since:"));
            assertThat(result.stdout(), containsString("Clock Difference:"));
            assertThat(result.stdout(), not(containsString("Permission Denied"))); // No permission denied messages
        }

        @Test
        void shouldShowVerboseDetailsForPermittedNodeAndPermissionDeniedForNonPermittedNode() throws Exception {
            // Set up a security realm with a user "user"
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

            // Create two online nodes
            DumbSlave nodeWithPermission = j.createOnlineSlave();
            nodeWithPermission.setLabelString("node-with-permission");
            String nodeWithPermissionName = nodeWithPermission.getNodeName();

            DumbSlave nodeWithoutPermission = j.createOnlineSlave();
            nodeWithoutPermission.setLabelString("node-without-permission");
            String nodeWithoutPermissionName = nodeWithoutPermission.getNodeName();

            AuthorizationStrategy auth = authorizationWithExtendedReadForNode(nodeWithPermissionName);
            j.jenkins.setAuthorizationStrategy(auth);

            // Run command with verbose and labels to include both nodes
            CLICommandInvoker.Result result = command.asUser("user").invokeWithArgs("-verbose", "-labels", "node-with-permission,node-without-permission", "-labels-mode", "ANY");

            // Verify the command runs successfully
            assertThat(result.returnCode(), is(0));
            assertThat(result.stderr(), is(emptyString())); // No errors in stderr

            // Verify both nodes are listed
            assertThat(result.stdout(), containsString(nodeWithPermissionName));
            assertThat(result.stdout(), containsString(nodeWithoutPermissionName));

            // Verify verbose details for node with permission
            assertThat(result.stdout(), containsString(nodeWithPermissionName));
            assertThat(result.stdout(), containsString("OS:"));
            assertThat(result.stdout(), containsString("Architecture:"));
            assertThat(result.stdout(), containsString("Java Version:"));
            assertThat(result.stdout(), containsString("Connected Since:"));
            assertThat(result.stdout(), containsString("Clock Difference:"));
            assertThat(result.stdout(), not(containsString(nodeWithPermissionName + "\nOS: Permission Denied")));

            // Verify "Permission Denied" for node without permission
            assertThat(result.stdout(), containsString(nodeWithoutPermissionName));
            assertThat(result.stdout(), containsString("OS: Permission Denied"));
            assertThat(result.stdout(), containsString("Architecture: Permission Denied"));
            assertThat(result.stdout(), containsString("Java Version: Permission Denied"));
            assertThat(result.stdout(), containsString("Connected Since: Permission Denied"));
            assertThat(result.stdout(), containsString("Clock Difference: Permission Denied"));
        }

        @Test
        void shouldOutputJsonWithVerboseDetailsForPermittedNodeAndPermissionDeniedForNonPermittedNode() throws Exception {
            // Set up a security realm with a user "user"
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

            // Create two online nodes
            DumbSlave nodeWithPermission = j.createOnlineSlave();
            nodeWithPermission.setLabelString("node-with-permission");
            String nodeWithPermissionName = nodeWithPermission.getNodeName();

            DumbSlave nodeWithoutPermission = j.createOnlineSlave();
            nodeWithoutPermission.setLabelString("node-without-permission");
            String nodeWithoutPermissionName = nodeWithoutPermission.getNodeName();

            AuthorizationStrategy auth = authorizationWithExtendedReadForNode(nodeWithPermissionName);
            j.jenkins.setAuthorizationStrategy(auth);

            // Run command with JSON format, verbose, and labels to include both nodes
            CLICommandInvoker.Result result = command.asUser("user").invokeWithArgs("-format", "JSON", "-verbose", "-labels", "node-with-permission,node-without-permission", "-labels-mode", "ANY");

            // Verify the command runs successfully
            assertThat(result.returnCode(), is(0));
            assertThat(result.stderr(), is(emptyString()));

            // Parse JSON output
            JSONArray jsonArray = JSONArray.fromObject(result.stdout());
            assertThat(jsonArray.size(), is(2)); // Both nodes should be listed

            // Find and verify each node
            boolean foundWithPermission = false;
            boolean foundWithoutPermission = false;

            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject jsonNode = jsonArray.getJSONObject(i);
                String displayName = jsonNode.getString("displayName");

                if (displayName.equals(nodeWithPermissionName)) {
                    foundWithPermission = true;
                    assertThat(jsonNode.getString("osDescription"), not(is("Permission Denied")));
                    assertThat(jsonNode.getString("architecture"), not(is("Permission Denied")));
                    assertThat(jsonNode.getString("javaVersion"), not(is("Permission Denied")));
                    assertThat(jsonNode.getString("connectedSince"), not(is("Permission Denied")));
                    assertThat(jsonNode.getString("clockDifference"), not(is("Permission Denied")));
                } else if (displayName.equals(nodeWithoutPermissionName)) {
                    foundWithoutPermission = true;
                    assertThat(jsonNode.getString("osDescription"), is("Permission Denied"));
                    assertThat(jsonNode.getString("architecture"), is("Permission Denied"));
                    assertThat(jsonNode.getString("javaVersion"), is("Permission Denied"));
                    assertThat(jsonNode.getString("connectedSince"), is("Permission Denied"));
                    assertThat(jsonNode.getString("clockDifference"), is("Permission Denied"));
                }
            }

            assertThat("Node with permission not found", foundWithPermission, is(true));
            assertThat("Node without permission not found", foundWithoutPermission, is(true));
        }
    }

    @Nested
    class NoNodesMatchingTests {
        @Test
        void shouldShowNoNodesMessageWhenNoNodesMatch() {
            CLICommandInvoker.Result result = command.invokeWithArgs("-exclude-controller");

            assertThat(result.stdout(), containsString("No nodes found matching the specified criteria"));
            assertThat(result.stderr(), is(emptyString()));
            assertThat(result.returnCode(), is(0));
        }
    }

    @Nested
    class JsonOutputTests {
        @Test
        void shouldOutputValidJsonFormat() throws Exception {
            DumbSlave node = j.createOnlineSlave();
            node.setLabelString("json-test");

            CLICommandInvoker.Result result = command.invokeWithArgs("-format", "JSON", "-labels", "json-test");

            String output = result.stdout();
            assertThat(output, containsString("\"displayName\": \"" + node.getNodeName() + "\""));
            assertThat(output, containsString("\"isOnline\": true"));
            assertThat(output, containsString("\"labels\":"));
            assertThat(result.stderr(), is(emptyString()));
            assertThat(result.returnCode(), is(0));

            // Verify JSON is parseable
            JSONArray jsonArray = JSONArray.fromObject(output);
            assertThat(jsonArray.size(), is(1));
            JSONObject jsonNode = jsonArray.getJSONObject(0);
            assertThat(jsonNode.getString("displayName"), is(node.getNodeName()));
        }
    }
}
