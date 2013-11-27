package jenkins.model;

import hudson.Extension;
import hudson.model.Saveable;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.util.PersistedList;

import java.io.IOException;

/**
 * Stores the colapsed status of all panes
 *
 * @author Bruno Meneguello<bruno@meneguello.com>
 */
public class PaneStatusProperty extends UserProperty implements Saveable {

	private final PersistedList<String> collapsed = new PersistedList<String>(this);

	public boolean isCollapsed(String paneId) {
		return collapsed.contains(paneId);
	}

	/**
	 * @param paneId panel name
	 * @return the actual state of panel
	 */
	public boolean toggleCollapsed(String paneId) throws IOException {
		if (collapsed.contains(paneId)) {
			collapsed.remove(paneId);
			return false;
		} else {
			collapsed.add(paneId);
			return true;
		}
	}

	public void save() throws IOException {
        user.save();
    }

	private Object readResolve() {
		collapsed.setOwner(this);
		return this;
	}

	@Extension
	public static class DescriptorImpl extends UserPropertyDescriptor {

		@Override
		public UserProperty newInstance(User user) {
			return new PaneStatusProperty();
		}

		@Override
		public String getDisplayName() {
			return null;
		}

		@Override
		public boolean isEnabled() {
			return false;
		}

	}

}
