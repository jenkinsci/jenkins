package hudson.remoting;

/**
 * Performs GC.
 * 
 * @author Kohsuke Kawaguchi
 */
class GCCommand extends Command {
    protected void execute(Channel channel) {
        System.gc();
    }

    private static final long serialVersionUID = 1L;
}
