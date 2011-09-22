/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Olivier Lamy
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

import hudson.model.BuildListener;
import jenkins.model.Jenkins;
import hudson.model.Result;
import hudson.remoting.DelegatingCallable;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

/**
 * @author Olivier Lamy
 *
 */
public abstract class AbstractMavenBuilder implements DelegatingCallable<Result,IOException> {
    
    /**
     * Goals to be executed in this Maven execution.
     */
    protected final List<String> goals;
    /**
     * Hudson-defined system properties. These will be made available to Maven,
     * and accessible as if they are specified as -Dkey=value
     */
    protected final Map<String,String> systemProps;
    /**
     * Where error messages and so on are sent.
     */
    protected final BuildListener listener;
    
    protected AbstractMavenBuilder(BuildListener listener, List<String> goals, Map<String, String> systemProps) {
        this.listener = listener;
        this.goals = goals;
        this.systemProps = systemProps;
    }
    
    protected String formatArgs(List<String> args) {
        StringBuilder buf = new StringBuilder("Executing Maven: ");
        for (String arg : args) {
            final String argPassword = "-Dpassword=" ;
            String filteredArg = arg ;
            // check if current arg is password arg. Then replace password by ***** 
            if (arg.startsWith(argPassword)) {
                filteredArg=argPassword+"*********";
            }
            buf.append(' ').append(filteredArg);
        }
        return buf.toString();
    }

    /**
     * Add all the {@link #systemProps jenkins environment variables} into the {@link System#getProperties() system properties}
     * Ignores {@link #systemProps jenkins environment variables} with empty keys.
     * @throws IllegalArgumentException if a {@link #systemProps jenkins environment variable} has null value
     *      as it blows up Maven.
     * @see http://jenkins.361315.n4.nabble.com/Upgrade-to-1-424-broke-our-Maven-builds-due-to-empty-system-property-key-td3726460.html
     */
    protected void registerSystemProperties() {
        for (Map.Entry<String,String> e : systemProps.entrySet()) {
            if ("".equals(e.getKey()))
                continue;
            if (e.getValue()==null)
                throw new IllegalArgumentException("Global Environment Variable "+e.getKey()+" has a null value");
            System.getProperties().put(e.getKey(), e.getValue());
        }
    }

    protected String format(NumberFormat n, long nanoTime) {
        return n.format(nanoTime/1000000);
    }

    // since reporters might be from plugins, use the uberjar to resolve them.
    public ClassLoader getClassLoader() {
        return Jenkins.getInstance().getPluginManager().uberClassLoader;
    }    
    
}
