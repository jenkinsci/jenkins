package hudson.tasks.junit;

import hudson.model.Action;

/**
 * 
 * Jelly (all optional):
 * <ul>
 * <li>index.jelly: included at the top of the test page</li>
 * <li>summary.jelly: included in a collapsed panel on the test parent page</li>
 * <li>badge.jelly: shown after the test link on the test parent page</li>
 * </ul>
 * 
 * @author tom
 *
 */
public abstract class TestAction implements Action {

	/**
	 * Returns text with annotations.
	 */
	public String annotate(String text) {
		return text;
	}

}
