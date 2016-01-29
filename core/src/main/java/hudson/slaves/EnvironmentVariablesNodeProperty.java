/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Tom Huybrechts
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

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.ComputerSet;
import hudson.model.Environment;
import hudson.model.Node;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * {@link NodeProperty} that sets additional environment variables.
 *
 * @since 1.286
 */
public class EnvironmentVariablesNodeProperty extends NodeProperty<Node> {

    /**
     * Agent-specific environment variables
     */
    private final EnvVars envVars;
    
    @DataBoundConstructor
    public EnvironmentVariablesNodeProperty(List<Entry> env) {
        this.envVars = toMap(env);
    }

    public EnvironmentVariablesNodeProperty(Entry... env) {
        this(Arrays.asList(env));
    }
	
    public EnvVars getEnvVars() {
    	return envVars;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
    	return Environment.create(envVars);
    }

    @Override
    public void buildEnvVars(EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        env.putAll(envVars);
    }

    @Extension
    public static class DescriptorImpl extends NodePropertyDescriptor {

        @Override
		public String getDisplayName() {
			return Messages.EnvironmentVariablesNodeProperty_displayName();
		}

        public String getHelpPage() {
            // yes, I know this is a hack.
            ComputerSet object = Stapler.getCurrentRequest().findAncestorObject(ComputerSet.class);
            if (object != null) {
                // we're on a node configuration page, show show that help page
                return "/help/system-config/nodeEnvironmentVariables.html";
            } else {
                // show the help for the global config page
                return "/help/system-config/globalEnvironmentVariables.html";
            }
        }
    }
	
	public static class Entry {
		public String key, value;

		@DataBoundConstructor
		public Entry(String key, String value) {
			this.key = key;
			this.value = value;
		}
	}
	
	private static EnvVars toMap(List<Entry> entries) {
		EnvVars map = new EnvVars();
        if (entries!=null)
            for (Entry entry: entries)
                map.put(entry.key,entry.value);
		return map;
	}

}
