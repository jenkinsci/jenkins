package hudson;

import static org.apache.commons.lang.ObjectUtils.defaultIfNull;

import javax.servlet.http.HttpSession;

import org.kohsuke.stapler.Stapler;

/**
 * Per {@link HttpSession} attribute
 *
 * @author Bruno Kühnen Meneguello <bruno@meneguello.com>
 * @since 1.541
 * @param <T> Type of attribute
 */
public class PerSession<T> {
	
	private static final String PREFIX = "hudson.data.";

	private final String name;
	
	private final T defaultValue;

	public PerSession(String name, T defaultValue) {
		this.name = name;
		this.defaultValue = defaultValue;
	}

	public T get() {
		return (T) defaultIfNull(session().getAttribute(name()), defaultValue);
	}
	
	public void set(T value) {
		session().setAttribute(name(), value);
	}
	
	public void unset() {
		session().removeAttribute(name());
	}

	private HttpSession session() {
		return Stapler.getCurrentRequest().getSession();
	}

	private String name() {
		return PREFIX + name;
	}
	
}
