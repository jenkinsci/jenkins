/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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

package hudson.slaves;

import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.slaves.NodeProvisioner.PlannedNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Future;

/**
 * Mock cloud implementation for testing the CloudProvisioningLimits system.
 *
 * This test cloud supports provisioning limits and allows configuration of
 * global and template-specific caps for testing over-provisioning prevention.
 *
 * @author Mike Cirioli
 * @since 2.530
 */
public class MockCloudWithLimits extends Cloud {

    private final int globalCap;
    private final int templateCap;
    private final boolean supportsLimits;
    private volatile int provisioningCount = 0;
    private final String templateId;

    /**
     * Creates a mock cloud with provisioning limits enabled.
     *
     * @param name the cloud name
     * @param globalCap the global provisioning cap (max executors across all templates)
     * @param templateCap the per-template provisioning cap
     * @param templateId the template identifier for testing template-specific limits
     */
    public MockCloudWithLimits(String name, int globalCap, int templateCap, String templateId) {
        super(name);
        this.globalCap = globalCap;
        this.templateCap = templateCap;
        this.templateId = templateId;
        this.supportsLimits = true;
    }

    /**
     * Creates a mock cloud without provisioning limits (for testing backward compatibility).
     *
     * @param name the cloud name
     */
    public MockCloudWithLimits(String name) {
        super(name);
        this.globalCap = Integer.MAX_VALUE;
        this.templateCap = Integer.MAX_VALUE;
        this.templateId = null;
        this.supportsLimits = false;
    }

    @Override
    public boolean supportsProvisioningLimits() {
        return supportsLimits;
    }

    @Override
    public int getGlobalProvisioningCap() {
        return globalCap;
    }

    @Override
    public int getTemplateProvisioningCap(String templateId) {
        if (this.templateId != null && this.templateId.equals(templateId)) {
            return templateCap;
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canProvision(CloudState state) {
        return true; // Always can provision (limits are checked elsewhere)
    }

    /**
     * Indicates whether this cloud supports no-delay provisioning.
     * This method is called by NoDelayProvisionerStrategy via reflection.
     *
     * @return true to indicate this mock cloud supports no-delay provisioning
     */
    public boolean isNoDelayProvisioning() {
        return true;
    }

    @Override
    public Collection<PlannedNode> provision(CloudState state, int excessWorkload) {
        Collection<PlannedNode> result = new ArrayList<>();

        for (int i = 0; i < excessWorkload; i++) {
            int nodeNumber = incrementProvisioningCount();
            String nodeName = name + "-" + (templateId != null ? templateId + "-" : "") + "node-" + nodeNumber;

            PlannedNode plannedNode = new PlannedNode(
                nodeName,
                createMockNodeFuture(nodeName),
                1 // Each node has 1 executor
            );
            result.add(plannedNode);
        }

        return result;
    }

    /**
     * Creates a mock Future that returns a test node.
     *
     * @param nodeName the name for the mock node
     * @return a Future that completes with a mock DumbSlave
     */
    private Future<Node> createMockNodeFuture(String nodeName) {
        return new Future<Node>() {
            private volatile boolean done = false;
            private volatile Node node = null;

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public Node get() {
                if (!done) {
                    // Simulate node creation
                    try {
                        node = new DumbSlave(
                            nodeName,
                            "/tmp/workspace",
                            new JNLPLauncher(true)
                        );
                        done = true;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create mock node", e);
                    }
                }
                return node;
            }

            @Override
            public Node get(long timeout, java.util.concurrent.TimeUnit unit) {
                return get();
            }
        };
    }

    /**
     * Gets the current provisioning count (for testing purposes).
     *
     * @return the number of nodes this cloud has provisioned
     */
    public synchronized int getProvisioningCount() {
        return provisioningCount;
    }

    /**
     * Resets the provisioning count (for test cleanup).
     */
    public synchronized void resetProvisioningCount() {
        provisioningCount = 0;
    }

    /**
     * Increments and returns the provisioning count (thread-safe).
     *
     * @return the incremented provisioning count
     */
    private synchronized int incrementProvisioningCount() {
        return ++provisioningCount;
    }

    /**
     * Gets the template ID used by this mock cloud.
     *
     * @return the template ID or null if not set
     */
    public String getTemplateId() {
        return templateId;
    }

    @Override
    public Descriptor<Cloud> getDescriptor() {
        return new DescriptorImpl();
    }

    /**
     * Descriptor for MockCloudWithLimits.
     */
    public static class DescriptorImpl extends Descriptor<Cloud> {
        @Override
        public String getDisplayName() {
            return "Mock Cloud With Limits";
        }
    }
}
