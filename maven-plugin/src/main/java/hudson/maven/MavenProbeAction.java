/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, CloudBees, Inc.
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
package hudson.maven;

import hudson.EnvVars;
import hudson.model.AbstractProject;
import hudson.model.Action;
import jenkins.model.Jenkins;
import hudson.remoting.Channel;
import hudson.util.RemotingDiagnostics;
import hudson.util.RemotingDiagnostics.HeapDump;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Map;

/**
 * UI for probing Maven process.
 *
 * <p>
 * This action is added to a build when it's started, and removed
 * when it's completed. 
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.175
 */
public final class MavenProbeAction implements Action {
    private final transient Channel channel;

    public final AbstractProject<?,?> owner;

    MavenProbeAction(AbstractProject<?,?> owner, Channel channel) {
        this.channel = channel;
        this.owner = owner;
    }

    public String getIconFileName() {
        if(channel==null)   return null;
        return "computer.png";
    }

    public String getDisplayName() {
        return Messages.MavenProbeAction_DisplayName();
    }

    public String getUrlName() {
        if(channel==null)   return null;
        return "probe";
    }

    /**
     * Gets the system properties of the JVM on this computer.
     * If this is the master, it returns the system property of the master computer.
     */
    public Map<Object,Object> getSystemProperties() throws IOException, InterruptedException {
        return RemotingDiagnostics.getSystemProperties(channel);
    }

    /**
     * Gets the environment variables of the JVM on this computer.
     * If this is the master, it returns the system property of the master computer.
     */
    public Map<String,String> getEnvVars() throws IOException, InterruptedException {
        return EnvVars.getRemote(channel);
    }

    /**
     * Gets the thread dump of the slave JVM.
     * @return
     *      key is the thread name, and the value is the pre-formatted dump.
     */
    public Map<String,String> getThreadDump() throws IOException, InterruptedException {
        return RemotingDiagnostics.getThreadDump(channel);
    }

    public void doScript( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        Jenkins._doScript(req, rsp, req.getView(this, "_script.jelly"), channel, owner.getACL());
    }

    /**
     * Obtains the heap dump.
     */
    public HeapDump getHeapDump() throws IOException {
        return new HeapDump(owner,channel);
    }
}
