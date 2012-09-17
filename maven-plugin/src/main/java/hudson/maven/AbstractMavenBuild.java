/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Red Hat, Inc., Victor Glushenkov
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
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.util.ReflectionUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractMavenBuild<P extends AbstractMavenProject<P,B>,B extends AbstractMavenBuild<P,B>> extends AbstractBuild<P, B>  {

    /**
     * Extra verbose debug switch.
     */
    public static boolean debug = false;
    
    protected AbstractMavenBuild(P job) throws IOException {
        super(job);
    }
    
    public AbstractMavenBuild(P job, Calendar timestamp) {
        super(job, timestamp);
    }
    
    public AbstractMavenBuild(P project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    @Override
    public EnvVars getEnvironment(TaskListener log) throws IOException, InterruptedException {
        EnvVars envs = super.getEnvironment(log);

        String opts = getMavenOpts(log,envs);

        if(opts!=null)
            envs.put("MAVEN_OPTS", opts);

        return envs;
    }

    /**
     * Obtains the fully resolved MAVEN_OPTS with all the tokens and variables expanded.
     *
     * @see MavenModuleSet#getMavenOpts()
     * @param envVars
     *      Caller must pass in the environment variables obtained from {@link #getEnvironment(TaskListener)}
     *      This method takes this as a parameter as opposed to recomputing it since the caller always have this handy.
     */
    public abstract String getMavenOpts(TaskListener listener, EnvVars envVars);

    /**
     * Expand tokens with token macro.
     */
    protected final String expandTokens(TaskListener listener, String str) {
        if (str==null)      return null;
        try {
            Class<?> clazz = Class.forName( "org.jenkinsci.plugins.tokenmacro.TokenMacro" );
            Method expandMethod =
                ReflectionUtils.findMethod(clazz, "expand", new Class[]{AbstractBuild.class, TaskListener.class, String.class});
            return (String) expandMethod.invoke( null, this, listener, str );
            //opts = TokenMacro.expand(this, listener, opts);
        }
        catch(Exception tokenException) {
            //Token plugin not present. Ignore, this is OK.
            LOGGER.log(Level.FINE, "Ignore problem in expanding tokens", tokenException);
        }
        catch(LinkageError linkageError) {
            // Token plugin not present. Ignore, this is OK.
            LOGGER.log(Level.FINE, "Ignore problem in expanding tokens", linkageError);
        }
        return str;
    }

    private static final Logger LOGGER = Logger.getLogger(AbstractMavenBuild.class.getName());
}
