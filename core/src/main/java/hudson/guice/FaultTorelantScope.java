package hudson.guice;

import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Scopes;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decorating {@link Scope} that tolerates a failure to instantiate an object.
 *
 * <p>
 * This is necessary as a failure to load one plugin shouldn't fail the startup of the entire Hudson.
 * Instead, we should just drop the failing plugins.
 * 
 * @author Kohsuke Kawaguchi
 */
public class FaultTorelantScope implements Scope {
    public static final Scope INSTANCE = new FaultTorelantScope();

    public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
        final Provider<T> base = Scopes.SINGLETON.scope(key,unscoped);
        return new Provider<T>() {
            public T get() {
                try {
                    return base.get();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,"Failed to instantiate",e);
                    return null;
                }
            }
        };
    }

    private static final Logger LOGGER = Logger.getLogger(FaultTorelantScope.class.getName());
}
