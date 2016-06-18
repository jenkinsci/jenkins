package hudson.model;

import static java.lang.String.format;
import hudson.Extension;
import hudson.util.PersistedList;

import java.io.IOException;

import javax.servlet.http.HttpSession;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.Stapler;

public class PaneStatusProperties extends UserProperty implements Saveable {
	
	private final PersistedList<String> collapsed = new PersistedList<String>(this);
	
	private static final PaneStatusProperties FALLBACK = new PaneStatusPropertiesSessionFallback();
	
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
	
	@Extension @Symbol("paneStatus")
	public static class DescriptorImpl extends UserPropertyDescriptor {

		@Override
		public UserProperty newInstance(User user) {
			return new PaneStatusProperties();
		}

		@Override
		public boolean isEnabled() {
			return false;
		}
		
	}
	
	private static class PaneStatusPropertiesSessionFallback extends PaneStatusProperties {
		
		private final String attribute = "jenkins_pane_%s_collapsed";
		
		@Override
		public boolean isCollapsed(String paneId) {
			final HttpSession session = Stapler.getCurrentRequest().getSession();
		    return session.getAttribute(format(attribute, paneId)) != null;
		}

		@Override
		public boolean toggleCollapsed(String paneId) {
			final HttpSession session = Stapler.getCurrentRequest().getSession();
		    final String property = format(attribute, paneId);
			final Object collapsed = session.getAttribute(property);
		    if (collapsed == null) {
		        session.setAttribute(property, true);
		        return true;
		    }
		    session.removeAttribute(property);
		    return false;
		}
	}

	public static PaneStatusProperties forCurrentUser() {
		final User current = User.current();
		if (current == null) {
			return FALLBACK;
		}
		return current.getProperty(PaneStatusProperties.class);
	}

}
