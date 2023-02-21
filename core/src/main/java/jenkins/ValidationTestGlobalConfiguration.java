package jenkins;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

@Extension
public class ValidationTestGlobalConfiguration extends GlobalConfiguration {

    private int someValue;

    @DataBoundConstructor
    public ValidationTestGlobalConfiguration() {}

    @DataBoundSetter
    public void setSomeValue(int someValue) {
        this.someValue = someValue;
    }

    public int getSomeValue() {
        return someValue;
    }
}
