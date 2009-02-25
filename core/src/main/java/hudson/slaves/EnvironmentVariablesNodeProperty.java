package hudson.slaves;

import hudson.Launcher;
import hudson.Extension;
import hudson.model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

public class EnvironmentVariablesNodeProperty extends NodeProperty<Node> {

	/**
     * Slave-specific environment variables
     */
    private final Map<String,String> envVars;
    
	@DataBoundConstructor
    public EnvironmentVariablesNodeProperty(List<Entry> env) {
        this.envVars = toMap(env);
    }

    public EnvironmentVariablesNodeProperty(Entry... env) {
        this(Arrays.asList(env));
    }
	
    public Map<String, String> getEnvVars() {
    	return envVars;
    }

    @Override
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
    	return Environment.create(envVars);
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
	
	private static Map<String,String> toMap(List<Entry> entries) {
		Map<String,String> map = new HashMap<String,String>();
		for (Entry entry: entries) {
			map.put(entry.key,entry.value);
		}
		return map;
	}

}
