/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Label;

/**
 * A request for cloud provisioning, including all data for the {@link Cloud} to be able to spin
 * up an instance with the correct settings.
 *
 * @author Nigel Magnay
 */
public class CloudProvisioningRequest {

  /**
   * The label requested.
   */
  private final Label label;

  /**
   * How many slaves are required.
   */
  private final int workloadToProvision;

  /**
   * The queue of actions, if we wish to spin up 'special variety'
   * slaves (and the cloud has this capability).
   */
  private final List<Actionable> queue;

  /**
   * A request to provision cloud executors.
   *
   * @param label
   *      The label that indicates what kind of nodes are needed now.
   *      Newly launched node needs to have this label.
   *      Only those {@link Label}s that this instance returned true
   *      from the {@link Cloud#canProvision(Label)} method will be passed here.
   *      This parameter is null if Jenkins needs to provision a new {@link hudson.model.Node}
   *      for jobs that don't have any tie to any label.
   * @param workloadToProvision
   *      Number of total executors needed to meet the current demand.
   *      Always >= 1. For example, if this is 3, the implementation
   *      should launch 3 slaves with 1 executor each, or 1 slave with
   *      3 executors, etc.
   * @param queue
   *      Collection of buildable items that are in the queue. Cloud implementations may
   *      use this information to influence the kinds of nodes that they start up
   *      (e.g: with specialised requirements based on the build).
   */
  CloudProvisioningRequest(Label label, int workloadToProvision,
                                  Collection<? extends Actionable> queue) {
    this.label = label;
    this.workloadToProvision = workloadToProvision;
    this.queue = new ArrayList<Actionable>(queue);
  }

  /**
   * Get the label for this provisioning request.
   * @return node label
   */
  public Label getLabel() {
    return label;
  }

  /**
   * How many slaves are required.
   * @return number of slaves
   */
  public int getWorkloadToProvision() {
    return workloadToProvision;
  }

  /**
   * What items are in the queue?
   * @return the items that are in the queue.
   */
  public Collection<Actionable> getBuildableItems() {
    return queue;
  }


}
