package hudson.model;

public class CauseAction implements Action {
	private Cause cause;
	public Cause getCause() {
		return cause;
	}
	
	public String getShortDescription() {
		return cause.getShortDescription();
	}
	
	public CauseAction(Cause c) {
		this.cause = c;
	}
	
	public String getDisplayName() {
		return "Cause";
	}

	public String getIconFileName() {
		// no icon
		return null;
	}

	public String getUrlName() {
		return "cause";
	}
}
