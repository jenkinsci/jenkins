package hudson.remoting;

import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Request/response pattern over {@link Command}.
 *
 * This is layer 1. This assumes that the receiving side has all the class definitions
 * available to de-serialize {@link Request}.
 *
 * @author Kohsuke Kawaguchi
 * @see Response
 */
abstract class Request<RSP extends Serializable,EXC extends Throwable> extends Command {

    /**
     * Executed on a remote system to perform the task.
     *
     * @param channel
     *      remote data channel.
     * @return
     *      the return value will be sent back to the calling process.
     * @throws EXC
     *      The exception will be forwarded to the calling process.
     *      If no checked exception is supposed to be thrown, use {@link RuntimeException}.
     */
    protected abstract RSP perform(Channel channel) throws EXC;

    /**
     * Uniquely identifies this request.
     */
    private final int id;

    private Response<RSP,EXC> response;

    protected Request() {
        synchronized(Request.class) {
            id = nextId++;
        }
    }

    /**
     * Sends this request to a remote system, and blocks until we receives a response.
     */
    public synchronized final RSP call(Channel channel) throws EXC, InterruptedException, IOException {
        channel.pendingCalls.put(id,this);
        channel.send(this);
        response=null;
        while(response==null)
            wait(); // wait until the response arrives

        if(response.exception!=null)
            throw response.exception;

        return response.returnValue;
    }

    /**
     * Called by the {@link Response} when we received it.
     */
    /*package*/ synchronized void onCompleted(Response<RSP,EXC> response) {
        this.response = response;
        notify();
    }

    /**
     * Aborts the processing. The calling thread will receive an exception. 
     */
    /*package*/ void abort(IOException e) {
        onCompleted(new Response(id,new RequestAbortedException(e)));
    }

    /**
     * Schedules the execution of this request.
     */
    protected final void execute(final Channel channel) {
        channel.executor.execute(new Runnable() {
            public void run() {
                try {
                    RSP rsp;
                    try {
                        rsp = Request.this.perform(channel);
                    } catch (Throwable t) {
                        // error return
                        channel.send(new Response<RSP,Throwable>(id,t));
                        return;
                    }
                    // normal completion
                    channel.send(new Response<RSP,EXC>(id,rsp));
                } catch (IOException e) {
                    // communication error.
                    // this means the caller will block forever
                    logger.log(Level.SEVERE, "Failed to send back a reply",e);
                }
            }
        });
    }

    /**
     * Next request ID.
     */
    private static int nextId=0;

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(Request.class.getName());
}
