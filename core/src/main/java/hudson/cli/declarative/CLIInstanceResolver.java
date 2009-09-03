package hudson.cli.declarative;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ClassParser;
import org.kohsuke.args4j.CmdLineException;
import hudson.model.Hudson;

/**
 * Used to parse a portion of arguments to resolve to a model object, for which {@link CLIMethod} will be invoked.
 *
 * <p>
 * This object is stateful. Typical subtype contains a number of fields and setter methods with args4j annotations.
 * The CLI framework creates an instance of {@link CLIInstanceResolver} from {@link CLIInstanceResolverFactory},
 * call {@link #defineOptionsTo(CmdLineParser)}, then let {@link CmdLineParser} parses the arguments, which
 * fills this object with parameters and arguments.
 *
 * <p>
 * Finally, the CLI framework will call {@link #resolve()} to obtain the object, and that is the object
 * that the command will act on.
 *
 * @author Kohsuke Kawaguchi
 * @see CLIInstanceResolverFactory
 * @since 1.324
 */
public abstract class CLIInstanceResolver<T> {
    /**
     * Fills the given {@link CmdLineParser} by defining options and arguments that this resolver uses.
     *
     * <p>
     * The default implementation expects the resolver instance itself to be annotated with args4j annotations.
     */
    public void defineOptionsTo(CmdLineParser parser) {
        new ClassParser().parse(this,parser);
    }

    /**
     * Called after {@link CmdLineParser} parsed arguments, to resolve the instance.
     *
     * @return
     *      must not be null.
     */
    public abstract T resolve() throws CmdLineException;
}
