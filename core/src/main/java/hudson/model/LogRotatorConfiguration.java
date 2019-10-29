/*
/* The MIT License
 * 
 * Copyright (c) 2019, Expedia Group
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
package hudson.model;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.tasks.LogRotator;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;

/**
 * @author awitt
 * @since 2.203
 *
 */
@Extension
public class LogRotatorConfiguration extends GlobalConfiguration {

    public static final boolean DEFAULT_ENABLE_ROTATION = true;
    
    /**
     * Whether or not {@link LogRotator} rules should be periodically applied to jobs.
     */
    protected boolean enableRotation = DEFAULT_ENABLE_ROTATION;
    
    public static final int DEFAULT_UPDATE_INTERVAL_HOURS = 24;
    
    /**
     * The number of hours to wait between applying {@link LogRotator} rules.
     */
    protected int updateIntervalHours = DEFAULT_UPDATE_INTERVAL_HOURS;
    
    /**
     * The last time that log rotation rules were applied.
     */
    protected long lastRotated;
    
    public static final LogRotationPolicy DEFAULT_POLICY_FOR_JOBS_WITH_ROTATORS = LogRotationPolicy.CUSTOM;
    
    /**
     * What to do for jobs that define their own custom {@link LogRotator}.
     */
    protected LogRotationPolicy policyForJobsWithCustomLogRotator = DEFAULT_POLICY_FOR_JOBS_WITH_ROTATORS;
    
    public static final LogRotationPolicy DEFAULT_POLICY_FOR_JOBS_WITHOUT_ROTATORS = LogRotationPolicy.NONE;
    
    /**
     * What to do for jobs that do not define their own custom {@link LogRotator}
     */
    protected LogRotationPolicy policyForJobsWithoutCustomLogRotator = DEFAULT_POLICY_FOR_JOBS_WITHOUT_ROTATORS;
    
    /**
     * Global log rotation policies
     */
    protected List<LogRotatorMapping> globalLogRotators = new ArrayList<LogRotatorMapping>();
    
    @DataBoundConstructor
    public LogRotatorConfiguration() {
        load();
    }
    
    public boolean isEnableRotation() {
        return enableRotation;
    }
    
    @DataBoundSetter
    public void setEnableRotation(boolean enableRotation) {
        this.enableRotation = enableRotation;
        
        save();
    }

    public int getUpdateIntervalHours() {
        return updateIntervalHours;
    }

    @DataBoundSetter
    public void setUpdateIntervalHours(int updateIntervalHours) {
        this.updateIntervalHours = updateIntervalHours;
        
        save();
    }

    public long getLastRotated() {
        return lastRotated;
    }

    /**
     * Not a {@link DataBoundSetter}; this should only be set by internal code,
     * never by an external user or process.
     * 
     * @param lastRotated The timestamp of the last time log rotation rules were applied.
     */
    public void setLastRotated(long lastRotated) {
        this.lastRotated = lastRotated;
        
        save();
    }

    public LogRotationPolicy getPolicyForJobsWithCustomLogRotator() {
        return policyForJobsWithCustomLogRotator;
    }

    @DataBoundSetter
    public void setPolicyForJobsWithCustomLogRotator(LogRotationPolicy policyForJobsWithCustomLogRotator) {
        this.policyForJobsWithCustomLogRotator = policyForJobsWithCustomLogRotator;
        
        save();
    }
    
    public LogRotationPolicy getPolicyForJobsWithoutCustomLogRotator() {
        return policyForJobsWithoutCustomLogRotator;
    }

    @DataBoundSetter
    public void setPolicyForJobsWithoutCustomLogRotator(LogRotationPolicy policyForJobsWithoutCustomLogRotator) {
        this.policyForJobsWithoutCustomLogRotator = policyForJobsWithoutCustomLogRotator;
        
        save();
    }
    public List<LogRotatorMapping> getGlobalLogRotators() {
        return globalLogRotators;
    }

    @DataBoundSetter
    public void setGlobalLogRotators(List<LogRotatorMapping> globalLogRotators) {
        this.globalLogRotators = globalLogRotators;
        
        save();
    }
    
    public FormValidation doCheckUpdateIntervalHours(@QueryParameter String updateIntervalHours) {
        try {
            int interval = Integer.parseInt( updateIntervalHours );
            
            if( interval <=0 ) {
                return FormValidation.error( Messages.LogRotatorConfiguration_pleaseEnterAPositiveInteger() );
            }
        } catch( NumberFormatException nfe ) {
            return FormValidation.error( Messages.LogRotatorConfiguration_pleaseEnterAPositiveInteger() );
        }
        
        return FormValidation.ok();
    }
    
    /**
     * How to rotate the logs of a given job.
     * 
     * @author awitt
     * @since 2.203
     */
    public static enum LogRotationPolicy {
        /**
         * Do not periodically rotate build logs.
         */
        NONE,
        
        /**
         * Use the job's own custom {@link LogRotator}.
         */
        CUSTOM,
        
        /**
         * Use an appropriate globally-defined {@link LogRotator}.
         * Or none, if there are no appropriate global definitions.
         * 
         * @see LogRotatorMapping
         */
        GLOBAL
    }

}
