package hudson.remoting;

/**
 * {@link Command} that unexports an object.
 * @author Kohsuke Kawaguchi
 */
public class UnexportCommand extends Command {
    private final int oid;

    public UnexportCommand(int oid) {
        this.oid = oid;
    }

    protected void execute(Channel channel) {
        channel.unexport(oid);
    }

    private static final long serialVersionUID = 1L;
}
