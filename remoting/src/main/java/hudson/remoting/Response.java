package hudson.remoting;

/**
 * Request/response pattern over {@link Command}.
 *
 * This is layer 1.
 *
 * @author Kohsuke Kawaguchi
 * @see Request
 */
final class Response<RSP,EXC extends Throwable> extends Command {
    /**
     * ID of the {@link Request} for which
     */
    private final int id;

    final RSP returnValue;
    final EXC exception;

    Response(int id, RSP returnValue) {
        this.id = id;
        this.returnValue = returnValue;
        this.exception = null;
    }

    Response(int id, EXC exception) {
        this.id = id;
        this.returnValue = null;
        this.exception = exception;
    }

    /**
     * Notifies the waiting {@link Request}.
     */
    @Override
    protected void execute(Channel channel) {
        Request req = channel.pendingCalls.get(id);
        if(req==null)
            return; // maybe aborted
        req.onCompleted(this);
        channel.pendingCalls.remove(id);
    }

    public String toString() {
        return "Response[retVal="+toString(returnValue)+",exception="+toString(exception)+"]";
    }

    private static String toString(Object o) {
        if(o==null) return "null";
        else        return o.toString();
    }

    private static final long serialVersionUID = 1L;
}
