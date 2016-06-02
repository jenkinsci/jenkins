/*
 * The MIT License
 *
 * Copyright 2015 Ted Shaw.
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

import hudson.Extension;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;

import java.util.*;

/**
 * Allow user to turn on/off selected experimental features. such features'
 * behavior subject to change without notice
 *
 * @since TODO update version after merge
 */
@Restricted(NoExternalUse.class)
@Extension
public class FeatureSwitchConfiguration extends GlobalConfiguration {

    //the feature list will be small, size less than 20
    private List<String> disabledFeatureStr = new ArrayList<String>();

    public EnumSet<Feature> getDisabledSet() {
        return disabledSet;
    }

    /**
     * Update the disabled feature list
     * used by {@link #configure(StaplerRequest req, JSONObject json)}.
     *
     * @param disabledSet
     */
    public void setDisabledSet(EnumSet<Feature> disabledSet) {
        this.disabledSet = disabledSet;
        this.disabledFeatureStr.clear();
        for (Feature feature : disabledSet) {
            disabledFeatureStr.add(feature.name());
        }
        save();
    }

    //Allow enum to be removed in future release, avoid serialization/deserialization
    transient EnumSet<Feature> disabledSet = EnumSet.noneOf(Feature.class);

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        return true;
    }

    public FeatureSwitchConfiguration() {
        //Load configure data from disk
        load();
        updateFeatureSet();
    }

    public static FeatureSwitchConfiguration get() {
        return GlobalConfiguration.all().get(FeatureSwitchConfiguration.class);
    }

    /**
     * Convert string to enum, removed feature(no matching enum item)
     * will be discarded
     */
    private void updateFeatureSet() {
        for (Feature feature : Feature.values()) {
            if (disabledFeatureStr.contains(feature.name())) {
                disabledSet.add(feature);
            }
        }
    }

    /**
     * Check the feature is enabled or not
     *
     * @param feature
     * @return a boolean flag indicating the feature disabled or not
     */
    public static boolean isDisabled(Feature feature) {
        return get().getDisabledSet().contains(feature);
    }

    public enum Feature {
        /**
         * User can leverage the feature to fast node remove
         * <pre>{@code
         *  SomeRetentionStrategy extends RetentionStrategy<SlaveComputer> implements ExecutorListener {
         *     @Override
         *     public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
         *         final SomeCloudSlave slaveNode=(SomeCloudSlave)executor.getOwner().getNode();
         *         if(slaveNode!=null && slaveNode.isOnetimeUse()){
         *             slaveNode.setAcceptingTasks(false);
         *             Computer.threadPoolForRemoting.submit(new Runnable() {
         *                 public void run() {
         *                     //some cleanup code here
         *                     //remove node after cleanup
         *                     Jenkins.getInstance().removeNode(slaveNode);
         *                 }
         *             });
         *         }
         *     }
         *  }
         * }</pre>
         */
        DEFER_EXECUTOR_CREATION(
                Messages.DeferExecutorCreation()
        );
        private String description;

        Feature(String description) {
            this.description = description;
        }

        /**
         * @return description of the feature
         */
        public String getDescription() {
            return description;
        }

        /**
         * Called in config page
         *
         * @return description of the feature
         */
        @Override
        public String toString() {
            return description;
        }

        /**
         * Always return true if feature is disabled, otherwise return the condition
         *
         * @param condition checked condition
         * @return true as passed, false as rejected
         */
        public boolean isOffOr(boolean condition) {
            return condition || isDisabled(this);
        }
    }
}
