package hudson.security;

public interface AccessControlled {

	void checkPermission(Permission permission);
	boolean hasPermission(Permission permission);
	
}
