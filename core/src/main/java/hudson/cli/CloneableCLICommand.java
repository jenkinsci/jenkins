package hudson.cli;

/**
 * {@link Cloneable} {@link CLICommand}.
 *
 * Uses {@link #clone()} instead of "new" to create a copy for exection.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class CloneableCLICommand extends CLICommand implements Cloneable {
    @Override
    protected CLICommand createClone() {
        try {
            return (CLICommand)clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
