package hudson.init;

import org.jvnet.hudson.reactor.ReactorListener;
import org.kohsuke.MetaInfServices;

/**
 * {@link ReactorListener}s that get notified of the Hudson initialization process.
 *
 * <p>
 * Because the act of initializing plugins is a part of the Hudson initialization,
 * this extension point cannot be implemented in a plugin. You need to place your jar
 * inside {@code WEB-INF/lib} instead.
 *
 * <p>
 * To register, put {@link MetaInfServices} on your implementation.
 *
 * @author Kohsuke Kawaguchi
 */
public interface InitReactorListener extends ReactorListener {
}
