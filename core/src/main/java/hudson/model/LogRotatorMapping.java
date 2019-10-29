/*
 * The MIT License
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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.Util;
import hudson.tasks.LogRotator;
import hudson.util.FormValidation;

/**
 * Maps a regular expression to a {@link LogRotator} definition.
 * 
 * @author awitt
 * @since 2.203
 *
 */
public class LogRotatorMapping extends AbstractDescribableImpl<LogRotatorMapping> {

    /**
     * A regular expression to apply to {@link Job#getFullName()}
     */
    protected String jobNameRegex;
    
    protected LogRotator logRotator;
        
    @DataBoundConstructor
    public LogRotatorMapping(String jobNameRegex, LogRotator logRotator) {
        this.jobNameRegex = jobNameRegex;
        this.logRotator = logRotator;
    }
    
    @DataBoundSetter
    public void setJobNameRegex(String jobNameRegex) {
        this.jobNameRegex = jobNameRegex;
    }
    
    public String getJobNameRegex() { return jobNameRegex; }
    
    @DataBoundSetter
    public void setLogRotator(LogRotator logRotator) {        
        this.logRotator = logRotator;
    }
    
    public LogRotator getLogRotator() { return logRotator; }
    
    @Extension
    public static class DescriptorImpl extends Descriptor<LogRotatorMapping> {
        
        @Override
        public String getDisplayName() {
            return "Log Rotator Mapping";
        }
        
        public FormValidation doCheckJobNameRegex(@QueryParameter String jobNameRegex) {
            
            if( null == Util.fixEmptyAndTrim(jobNameRegex) ) {
                return FormValidation.error( Messages.LogRotatorMapping_provideARegex() );
            }
            
            try {
                Pattern.compile( jobNameRegex );
                return FormValidation.ok();
            } catch( PatternSyntaxException pse ) {
                return FormValidation.error(pse, Messages.LogRotatorMapping_provideAValidRegex() );
            }
        }
    }
}