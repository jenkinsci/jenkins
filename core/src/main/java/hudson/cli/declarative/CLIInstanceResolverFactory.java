package hudson.cli.declarative;

import hudson.ExtensionPoint;
import hudson.Extension;


/**
 * Extension point for resolving an instance of a model object, from the arguments and options given to CLI.
 *
 * <p>
 * To have your implementation registered, put {@link Extension} on your implementation.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.324
 */
public abstract class CLIInstanceResolverFactory implements ExtensionPoint {
    /**
     * Creates a new instance of {@link CLIInstanceResolver} that can resolve the given type.
     *
     * @return
     *      null if this factory doens't understand the given type, in which case the caller
     *      will continue to search the rest of the factories for a match.
     */
    public abstract <T> CLIInstanceResolver<? extends T> create(Class<T> type);
}
