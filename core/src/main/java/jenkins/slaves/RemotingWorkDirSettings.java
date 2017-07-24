/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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
package jenkins.slaves;

import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.slaves.SlaveComputer;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Defines settings of the Remoting work directory.
 * 
 * This class contains Remoting Work Directory settings, which can be used when starting Jenkins agents. 
 * See <a href="https://github.com/jenkinsci/remoting/blob/master/docs/workDir.md">Remoting Work Dir Documentation</a>.
 * 
 * @author Oleg Nenashev
 * @since TODO
 */
public class RemotingWorkDirSettings implements Describable<RemotingWorkDirSettings> {
    
    private static final String DEFAULT_INTERNAL_DIR = "remoting";
    private static final RemotingWorkDirSettings LEGACY_DEFAULT = new RemotingWorkDirSettings(true, null, DEFAULT_INTERNAL_DIR, false);
    
    private final boolean disabled;
    @CheckForNull
    private final String  workDirPath;
    @Nonnull
    private final String  internalDir;
    private final boolean failIfWorkDirIsMissing;

    @DataBoundConstructor
    public RemotingWorkDirSettings(boolean disabled, 
            @CheckForNull String workDirPath, @CheckForNull String internalDir, 
            boolean failIfWorkDirIsMissing) {
        this.disabled = disabled;
        this.workDirPath = Util.fixEmptyAndTrim(workDirPath);
        this.failIfWorkDirIsMissing = failIfWorkDirIsMissing;
        String internalDirName = Util.fixEmptyAndTrim(internalDir);
        this.internalDir = internalDirName != null ? internalDirName : DEFAULT_INTERNAL_DIR;
    }

    public RemotingWorkDirSettings() {
        // Enabled by default
        this(false, null, DEFAULT_INTERNAL_DIR, false);
    }

    /**
     * Check if workdir is disabled.
     * 
     * @return {@code true} if the property is disabled.
     *         In such case Remoting will use the legacy mode.
     */
    public boolean isDisabled() {
        return disabled;
    }
    
    /**
     * Indicates that agent root directory should be used as work directory.
     * 
     * @return {@code true} if the agent root should be a work directory.
     */
    public boolean isUseAgentRootDir() {
        return workDirPath == null;
    }

    /**
     * Check if startup should fail if the workdir is missing.
     * 
     * @return {@code true} if Remoting should fail if the the work directory is missing instead of creating it
     */
    public boolean isFailIfWorkDirIsMissing() {
        return failIfWorkDirIsMissing;
    }

    /**
     * Gets path to the custom workdir path.
     * 
     * @return Custom workdir path.
     *         If {@code null}, an agent root directory path should be used instead.
     */
    @CheckForNull
    public String getWorkDirPath() {
        return workDirPath;
    }

    @Nonnull
    public String getInternalDir() {
        return internalDir;
    }

    @Override
    public Descriptor<RemotingWorkDirSettings> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(RemotingWorkDirSettings.class);
    }
    
    /**
     * Gets a command line string, which can be passed to agent start command.
     * 
     * @param computer Computer, for which the arguments need to be constructed.
     * @return Command line arguments. 
     *         It may be empty if the working directory is disabled or
     *         if the Computer type is not {@link SlaveComputer}.
     */
    @Nonnull
    public String toCommandLineString(@Nonnull SlaveComputer computer) {
        if(disabled) {
            return "";
        }
        
        StringBuilder bldr = new StringBuilder();
        bldr.append("-workDir \"");
        if (workDirPath == null) {
            Slave node = computer.getNode();
            if (node == null) {
                // It is not possible to launch this node anyway.
                return "";
            }
            bldr.append(node.getRemoteFS());
        } else {
            bldr.append(workDirPath);
        }
        bldr.append("\"");
        
        if (!DEFAULT_INTERNAL_DIR.equals(internalDir)) {
            bldr.append(" -internalDir \"");
            bldr.append(internalDir);
            bldr.append("\"");
        }
        
        if (failIfWorkDirIsMissing) {
            bldr.append(" -failIfWorkDirIsMissing"); 
        }
                
        return bldr.toString();
    }
    
    @Extension
    public static class DescriptorImpl extends Descriptor<RemotingWorkDirSettings> {
        
    }
    
    /**
     * Gets legacy value for the migrated agents.
     * 
     * @return Legacy value: disabled work directory.
     */
    public static RemotingWorkDirSettings getDefaultLegacyValue() {
        return LEGACY_DEFAULT;
    }
}
