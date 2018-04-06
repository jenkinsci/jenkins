package hudson.model;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;

@Extension @Symbol("slaveComputer")
public class SlaveComputerConfiguration extends GlobalConfiguration {
    private boolean rejectConnection;

    public static SlaveComputerConfiguration get() {
        return GlobalConfiguration.all().get(SlaveComputerConfiguration.class);
    }

    public SlaveComputerConfiguration() {
        load();
    }
    
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        json = json.getJSONObject("remoting");
        rejectConnection = json.getBoolean("rejectConnection");
        save();
        return true;
    }

    public boolean isRejectConnection() {
        return rejectConnection;
    }
}