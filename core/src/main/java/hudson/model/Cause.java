package hudson.model;

import hudson.triggers.Trigger;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Cause object base class.  This class hierarchy is used to keep track of why 
 * a given build was started.   The Cause object is connected to a build via the
 * CauseAction object.
 *
 * @author Michael Donohue
 */
public abstract class Cause {
	abstract public String getShortDescription();
	
	@ExportedBean
	public static class LegacyCodeCause extends Cause {
		private StackTraceElement [] stackTrace;
		public LegacyCodeCause() {
			stackTrace = new Exception().getStackTrace();
		}
		
		@Override
		@Exported
		public String getShortDescription() {
			return "Legacy code started this job.  No cause information is available";
		}
	}
	
	@ExportedBean
	public static class UpstreamCause extends Cause {
		private String upstreamProject;
		private int upstreamBuild;
		private Cause upstreamCause;
		
		public UpstreamCause(AbstractBuild<?, ?> up) {
			upstreamBuild = up.getNumber();
			upstreamProject = up.getProject().getName();
			CauseAction ca = up.getAction(CauseAction.class);
			upstreamCause = ca == null ? null : ca.getCause();
		}
		
		@Override
		@Exported
		public String getShortDescription() {
			return "Started by upstream project \"" + upstreamProject + "\" build number " + upstreamBuild;
		}
	}

	@ExportedBean
	public static class UserCause extends Cause {
		private String authenticationName;
		public UserCause() {
			this.authenticationName = Hudson.getAuthentication().getName();
		}
		
		@Override
		@Exported
		public String getShortDescription() {
			return "Started by user " + authenticationName;
		}
	}
}
