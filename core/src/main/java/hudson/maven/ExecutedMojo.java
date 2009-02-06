/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.Util;
import hudson.model.Hudson;
import hudson.remoting.Which;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.kohsuke.stapler.Stapler;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Persisted record of mojo execution.
 *
 * <p>
 * This information is first recorded in the maven process, then sent over
 * the remoting layer to the master, then persisted via XStream.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ExecutedMojo implements Serializable {
    /**
     * Plugin group ID.
     */
    public final String groupId;
    /**
     * Plugin artifact ID.
     */
    public final String artifactId;
    /**
     * Plugin version.
     */
    public final String version;
    /**
     * Mojo name.
     */
    public final String goal;
    /**
     * Optional execution ID, when the same mojo gets executed multiple times.
     */
    public final String executionId;
    /**
     * How long did it take to execute this goal? in milli-seconds.
     * (precision might not be 1ms)
     */
    public final long duration;
    /**
     * MD5 hash of the plugin jar.
     */
    public final String digest;

    ExecutedMojo(MojoInfo mojo, long duration) throws IOException, InterruptedException {
        this.groupId = mojo.pluginName.groupId;
        this.artifactId = mojo.pluginName.artifactId;
        this.version = mojo.pluginName.version;
        this.goal = mojo.getGoal();
        this.executionId = mojo.mojoExecution.getExecutionId();
        this.duration = duration;

        String digest = null;
        MojoDescriptor md = mojo.mojoExecution.getMojoDescriptor();
        PluginDescriptor pd = md.getPluginDescriptor();
        try {
            Class clazz = pd.getClassRealm().loadClass(md.getImplementation());
            digest = Util.getDigestOf(new FileInputStream(Which.jarFile(clazz)));
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Failed to locate jar for "+md.getImplementation(),e);
        } catch (ClassNotFoundException e) {
            // perhaps the plugin has failed to load.
        }
        this.digest = digest;
    }

    /**
     * Returns duration in a human readable text.
     */
    public String getDurationString() {
        return Util.getTimeSpanString(duration);
    }

    public String getReadableExecutionId() {
        if(executionId==null)   return "-";
        else                    return executionId;
    }

    /**
     * Returns a hyperlink for the plugin name if there's one.
     * Otherwise null.
     */
    public String getPluginLink(Cache c) {
        MavenModule m = c.get(this);
        if(m!=null)
            return Stapler.getCurrentRequest().getContextPath()+m.getUrl();
        if(groupId.equals("org.apache.maven.plugins"))
            return "http://maven.apache.org/plugins/"+artifactId+'/';
        return null;
    }

    public String getGoalLink(Cache c) {
        if(groupId.equals("org.apache.maven.plugins"))
            return "http://maven.apache.org/plugins/"+artifactId+'/'+goal+"-mojo.html";
        return null;
    }

    /**
     * Used during the HTML rendering to cache the index.
     */
    public static final class Cache {
        /**
         * All maven modules in this Hudson by their names.
         */
        public final Map<ModuleName,MavenModule> modules = new HashMap<ModuleName,MavenModule>();

        public Cache() {
            for( MavenModule m : Hudson.getInstance().getAllItems(MavenModule.class))
                modules.put(m.getModuleName(),m);
        }

        public MavenModule get(ExecutedMojo mojo) {
            return modules.get(new ModuleName(mojo.groupId,mojo.artifactId));
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ExecutedMojo.class.getName());
}
