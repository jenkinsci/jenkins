package hudson.remoting;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a communication channel to the remote peer.
 *
 * @author Kohsuke Kawaguchi
 */
public class Channel {
    private final ObjectInputStream ois;
    private final ObjectOutputStream oos;
    /*package*/ final Executor executor;

    private final ReaderThread reader;

    /**
     * If true, this data channel is already closed and
     * no further calls are accepted.
     */
    private boolean closed = false;

    /*package*/ final Map<Integer,Request<?,?>> pendingCalls = new Hashtable<Integer,Request<?,?>>();

    /**
     * When sending a class definition to the other end, a classloader needed for it will be registered
     * in this table.
     */
    /*package*/ final ExportedClassLoaderTable exportedClassLoaders = new ExportedClassLoaderTable();

    /**
     * {@link ClassLoader}s that are proxies of the remote classloaders.
     */
    /*package*/ final ImportedClassLoaderTable importedClassLoaders = new ImportedClassLoaderTable(this);

    /**
     *
     * @param name
     *      Human readable name of this channel. Used for debug/logging. Can be anything.
     * @param exec
     *      Commands sent from the remote peer will be executed by using this {@link Executor}.
     * @param is
     *      Stream connected to the remote peer.
     * @param os
     *      Stream connected to the remote peer.
     */
    public Channel(String name, Executor exec, InputStream is, OutputStream os) throws IOException {
        this.executor = exec;
        this.oos = new ObjectOutputStream(os);
        this.ois = new ObjectInputStream(is);
        this.reader = new ReaderThread(name);
        reader.start();
    }

    /**
     * Sends a command to the remote end and executes it there.
     */
    /*package*/ synchronized void send(Command cmd) throws IOException {
        if(closed)
            throw new IOException("already closed");
        logger.fine("Send "+cmd);
        oos.writeObject(cmd);
        oos.reset();
    }

    /**
     * Makes a remote procedure call.
     */
    public <V extends Serializable,T extends Throwable>
    V call(Callable<V,T> callable) throws IOException, T, InterruptedException {
        UserResponse<V> r = new UserRequest<V,T>(this, callable).call(this);
        try {
            return r.retrieve(this);
        } catch (ClassNotFoundException e) {
            // this is unlikely to happen, so this is a lame implementation
            IOException x = new IOException();
            x.initCause(e);
            throw x;
        }
    }

    private synchronized void terminate(IOException e) {
        // abort
        closed = true;
        synchronized(pendingCalls) {
            for (Request<?,?> req : pendingCalls.values())
                req.abort(e);
            pendingCalls.clear();
        }
        notify();
    }

    /**
     * Waits for the close down of this {@link Channel}.
     */
    public synchronized void join() throws InterruptedException {
        while(!closed)
            wait();
    }

    /**
     * Notifies the remote peer that we are closing down.
     */
    private static final class CloseCommand extends Command {
        protected void execute(Channel channel) {
            try {
                channel.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE,"close command failed",e);
            }
        }

        public String toString() {
            return "close";
        }
    }

    /**
     * Performs an orderly shut down of this channel (and the remote peer.)
     */
    public void close() throws IOException {
        if(closed)  return;

        send(new CloseCommand());
        
        // TODO: would be nice if we can wait for the completion of pending requests
        terminate(null);
    }

    private final class ReaderThread extends Thread {
        public ReaderThread(String name) {
            super("DataChannel reader thread: "+name);
        }

        public void run() {
            try {
                while(!closed) {
                    try {
                        Command cmd = (Command)ois.readObject();
                        logger.fine("Received "+cmd);
                        cmd.execute(Channel.this);
                    } catch (ClassNotFoundException e) {
                        logger.log(Level.SEVERE, "Unabled to read a command",e);
                    }
                }
                ois.close();
                oos.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "I/O error in DataChannel",e);
                terminate(e);
            }
        }
    }

    private static final Logger logger = Logger.getLogger(Channel.class.getName());
}
