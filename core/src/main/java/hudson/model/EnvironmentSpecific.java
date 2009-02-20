package hudson.model;

import java.util.Map;

/**
 * Represents any concept that can be adapted for a certain environment.
 * 
 * Mainly for documentation purposes.
 *
 * @param <T>
 */
public interface EnvironmentSpecific<T> {

	/**
	 * Returns a specialized copy of T for functioning in the given environment.
	 */
	T forEnvironment(Map<String,String> environment);
	
}
