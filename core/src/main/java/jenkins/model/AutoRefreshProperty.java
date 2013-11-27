package jenkins.model;

import hudson.Extension;
import hudson.model.Messages;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Stores actual state of auto-refresh
 *
 * @author Bruno Meneguello<bruno@meneguello.com>
 */
public class AutoRefreshProperty extends UserProperty {

	private boolean autoRefresh = false;

	public AutoRefreshProperty() {

	}

	@DataBoundConstructor
	public AutoRefreshProperty(boolean autoRefresh) {
	    this.autoRefresh = autoRefresh;
	}

	public void setAutoRefresh(boolean autoRefresh) throws IOException {
        this.autoRefresh = autoRefresh;
        user.save();
    }

	public boolean isAutoRefresh() {
        return autoRefresh;
    }

    @Extension
    public static class DescriptorImpl extends UserPropertyDescriptor {

        @Override
        public UserProperty newInstance(User user) {
            return new AutoRefreshProperty();
        }

        @Override
        public String getDisplayName() {
            return Messages.AutoRefreshProperty_GlobalAction_DisplayName();
        }

	}

}
