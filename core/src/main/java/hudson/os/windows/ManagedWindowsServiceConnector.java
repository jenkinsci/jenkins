package hudson.os.windows;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.slaves.ComputerConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * {@link ComputerConnector} that delegates to {@link ManagedWindowsServiceLauncher}.
 * @author Kohsuke Kawaguchi
 */
public class ManagedWindowsServiceConnector extends ComputerConnector {
    /**
     * "[DOMAIN\\]USERNAME" to follow the Windows convention.
     */
    public final String userName;

    public final Secret password;

    @DataBoundConstructor
    public ManagedWindowsServiceConnector(String userName, String password) {
        this.userName = userName;
        this.password = Secret.fromString(password);
    }

    @Override
    public ManagedWindowsServiceLauncher launch(final String hostName, TaskListener listener) throws IOException, InterruptedException {
        return new ManagedWindowsServiceLauncher(userName,Secret.toString(password),hostName);
    }

    @Extension
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        public String getDisplayName() {
            return Messages.ManagedWindowsServiceLauncher_DisplayName();
        }

        // used by Jelly
        public static final Class<?> CONFIG_DELEGATE_TO = ManagedWindowsServiceLauncher.class;
    }
}
