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

import static hudson.Util.intern;
import hudson.Util;
import jenkins.model.Jenkins;
import hudson.remoting.Which;
import hudson.util.ReflectionUtils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.kohsuke.stapler.Stapler;

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
    private static final long serialVersionUID = -3048316415397586490L;
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

    public ExecutedMojo(MojoInfo mojo, long duration) {
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
            Class<?> clazz = getMojoClass( md, pd );
            if (clazz!=null) {
                File jarFile = Which.jarFile(clazz);
                if (jarFile.isFile()) {
                    digest = Util.getDigestOf(jarFile);
                } else {
                    // Maybe mojo was loaded from a classes dir instead of from a jar (JENKINS-5044)
                    LOGGER.log(Level.WARNING, "Cannot calculate digest of mojo class, because mojo wasn't loaded from a jar, but from: "
                            + jarFile);
                }
            } else {
                LOGGER.log(Level.WARNING, "Failed to getClass for "+md.getImplementation());    
            }
            
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Failed to locate jar for "+md.getImplementation(),e);
        } catch (ClassNotFoundException e) {
            // perhaps the plugin has failed to load.
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to calculate digest for "+md.getImplementation(),e);
        }
        this.digest = digest;
    }
    
    private Class<?> getMojoClass(MojoDescriptor md, PluginDescriptor pd) throws ClassNotFoundException {
        try {
            ClassRealm classRealm = pd.getClassRealm();
            return classRealm == null ? null : classRealm.loadClass( md.getImplementation() );
        } catch (NoSuchMethodError e) {
            // maybe we are in maven2 build ClassRealm package has changed
            return getMojoClassForMaven2( md, pd );
        }
    }
    
    private Class<?> getMojoClassForMaven2(MojoDescriptor md, PluginDescriptor pd) throws ClassNotFoundException {
        
        Method method = ReflectionUtils.getPublicMethodNamed( pd.getClass(), "getClassRealm" );
        
        org.codehaus.classworlds.ClassRealm cl = 
            (org.codehaus.classworlds.ClassRealm) ReflectionUtils.invokeMethod( method, pd );
        
        if (cl==null)
        {
            return null;
        }
        Class<?> clazz = cl.loadClass( md.getImplementation() );
        return clazz;
       
    }
    

    /**
     * Copy constructor used for interning.
     */
    private ExecutedMojo(String groupId, String artifactId, String version, String goal, String executionId, long duration, String digest) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.goal = goal;
        this.executionId = executionId;
        this.duration = duration;
        this.digest = digest;
    }

    /**
     * Lots of {@link ExecutedMojo}s tend to have the same groupId, artifactId, etc., so interning them help
     * with memory consumption.
     *
     * TODO: better if XStream has a declarative way of marking fields as "target for intern".
     */
    protected Object readResolve() {
        return new ExecutedMojo(intern(groupId),intern(artifactId),intern(version),intern(goal),intern(executionId),duration,intern(digest));
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
        if (groupId.equals("org.codehaus.mojo"))
            return "http://mojo.codehaus.org/"+artifactId+'/';
        return null;
    }

    public String getGoalLink(Cache c) {
        if(groupId.equals("org.apache.maven.plugins"))
            return "http://maven.apache.org/plugins/"+artifactId+'/'+goal+"-mojo.html";
        if (groupId.equals("org.codehaus.mojo"))
            return "http://mojo.codehaus.org/"+artifactId+'/'+goal+"-mojo.html";
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
            for( MavenModule m : Jenkins.getInstance().getAllItems(MavenModule.class))
                modules.put(m.getModuleName(),m);
        }

        public MavenModule get(ExecutedMojo mojo) {
            return modules.get(new ModuleName(mojo.groupId,mojo.artifactId));
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ExecutedMojo.class.getName());
}
