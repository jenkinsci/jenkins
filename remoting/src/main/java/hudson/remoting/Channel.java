package hudson.remoting;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a communication channel to the remote peer.
 *
 * <p>
 * A {@link Channel} is a mechanism for two JVMs to communicate over
 * bi-directional {@link InputStream}/{@link OutputStream} pair.
 * {@link Channel} represents an endpoint of the stream, and thus
 * two {@link Channel}s are always used in a pair.
 *
 * <p>
 * Communication is established as soon as two {@link Channel} instances
 * are created at the end fo the stream pair
 * until the stream is terminated via {@link #close()}.
 *
 * <p>
 * The basic unit of remoting is an executable {@link Callable} object.
 * An application can create a {@link Callable} object, and execute it remotely
 * by using the {@link #call(Callable)} method or {@link #callAsync(Callable)} method.
 *
 * <p>
 * In this sense, {@link Channel} is a mechanism to delegate/offload computation
 * to other JVMs and somewhat like an agent system. This is bit different from
 * remoting technologies like CORBA or web services, where the server exposes a
 * certain functionality that clients invoke. 
 *
 * <p>
 * {@link Callable} object, as well as the return value / exceptions,
 * are transported by using Java serialization. All the necessary class files
 * are also shipped over {@link Channel} on-demand, so there's no need to
 * pre-deploy such classes on both JVMs. 
 *
 *
 * <h2>Implementor's Note</h2>
 * <p>
 * {@link Channel} builds its features in a layered model. Its higher-layer
 * features are built on top of its lower-layer features, and they
 * are called layer-0, layer-1, etc.
 *
 * <ul>
 * <li>
 *  <b>Layer 0</b>:
 *  See {@link Command} for more details. This is for higher-level features,
 *  and not likely useful for applications directly.
 * <li>
 *  <b>Layer 1</b>:
 *  See {@link Request} for more details. This is for higher-level features,
 *  and not likely useful for applications directly.
 * </ul>
 *
 * @author Kohsuke Kawaguchi
 */
public class Channel implements VirtualChannel {
    private final ObjectInputStream ois;
    private final ObjectOutputStream oos;
    private final String name;
    /*package*/ final Executor executor;

    /**
     * If true, the incoming link is already shut down,
     * and reader is already terminated.
     */
    private volatile boolean inClosed = false;
    /**
     * If true, the outgoing link is already shut down,
     * and no command can be sent.
     */
    private volatile boolean outClosed = false;

    /*package*/ final Map<Integer,Request<?,?>> pendingCalls = new Hashtable<Integer,Request<?,?>>();

    /**
     * {@link ClassLoader}s that are proxies of the remote classloaders.
     */
    /*package*/ final ImportedClassLoaderTable importedClassLoaders = new ImportedClassLoaderTable(this);

    /**
     * Objects exported via {@link #export(Class, Object)}.
     */
    private final ExportTable<Object> exportedObjects = new ExportTable<Object>();

    /**
     * Registered listeners. 
     */
    private final Vector<Listener> listeners = new Vector<Listener>();
    private int gcCounter;

    public Channel(String name, Executor exec, InputStream is, OutputStream os) throws IOException {
        this(name,exec,is,os,null);
    }

    /**
     * Creates a new channel.
     * 
     * @param name
     *      Human readable name of this channel. Used for debug/logging. Can be anything.
     * @param exec
     *      Commands sent from the remote peer will be executed by using this {@link Executor}.
     * @param is
     *      Stream connected to the remote peer.
     * @param os
     *      Stream connected to the remote peer.
     * @param header
     *      If non-null, receive the portion of data in <tt>is</tt> before
     *      the data goes into the "binary mode". This is useful
     *      when the established communication channel might include some data that might
     *      be useful for debugging/trouble-shooting.
     */
    public Channel(String name, Executor exec, InputStream is, OutputStream os, OutputStream header) throws IOException {
        this.name = name;
        this.executor = exec;

        // write the magic preamble.
        // certain communication channel, such as forking JVM via ssh,
        // may produce some garbage at the beginning (for example a remote machine
        // might print some warning before the program starts outputting its own data.)
        //
        // so use magic preamble and discard all the data up to that to improve robustness.
        os.write(new byte[]{0,0,0,0}); // preamble
        this.oos = new ObjectOutputStream(os);
        oos.flush();    // make sure that stream header is sent to the other end. avoids dead-lock

        {// read the input until we hit preamble
            int ch;
            int count=0;

            while(true) {
                ch = is.read();
                if(ch==-1) {
                    throw new EOFException("unexpected stream termination");
                }
                if(ch==0) {
                    count++;
                    if(count==4)    break;
                } else {
                    if(header!=null)
                        header.write(ch);
                    count=0;
                }
            }
        }

        this.ois = new ObjectInputStream(is);
        new ReaderThread(name).start();
    }

    /**
     * Callback "interface" for changes in the state of {@link Channel}.
     */
    public static abstract class Listener {
        /**
         * When the channel was closed normally or abnormally due to an error.
         *
         * @param cause
         *      if the channel is closed abnormally, this parameter
         *      represents an exception that has triggered it.
         */
        public void onClosed(Channel channel, IOException cause) {}
    }

    /**
     * Sends a command to the remote end and executes it there.
     *
     * <p>
     * This is the lowest layer of abstraction in {@link Channel}.
     * {@link Command}s are executed on a remote system in the order they are sent.
     */
    /*package*/ synchronized void send(Command cmd) throws IOException {
        if(outClosed)
            throw new IOException("already closed");
        if(logger.isLoggable(Level.FINE))
            logger.fine("Send "+cmd);
        Channel old = Channel.setCurrent(this);
        try {
            oos.writeObject(cmd);
            oos.flush();        // make sure the command reaches the other end.
        } finally {
            Channel.setCurrent(old);
        }
        oos.reset();
    }

    /**
     * {@inheritDoc}
     */
    public <T> T export(Class<T> type, T instance) {
        return export(type,instance,true);
    }

    /**
     * @param userProxy
     *      If true, the returned proxy will be capable to handle classes
     *      defined in the user classloader as parameters and return values.
     *      Such proxy relies on {@link RemoteClassLoader} and related mechanism,
     *      so it's not usable for implementing lower-layer services that are
     *      used by {@link RemoteClassLoader}.
     *
     *      To create proxies for objects inside remoting, pass in false. 
     */
    /*package*/ <T> T export(Class<T> type, T instance, boolean userProxy) {
        if(instance==null)
            return null;

        // every so often perform GC on the remote system so that
        // unused RemoteInvocationHandler get released, which triggers
        // unexport operation.
        if((++gcCounter)%10000==0)
            try {
                send(new GCCommand());
            } catch (IOException e) {
                // for compatibility reason we can't change the export method signature
                logger.log(Level.WARNING, "Unable to send GC command",e);
            }

        // proxy will unexport this instance when it's GC-ed on the remote machine.
        final int id = export(instance);
        return type.cast(Proxy.newProxyInstance( type.getClassLoader(), new Class[]{type},
            new RemoteInvocationHandler(id,userProxy)));
    }

    /*package*/ int export(Object instance) {
        return exportedObjects.export(instance);
    }

    /*package*/ Object getExportedObject(int oid) {
        return exportedObjects.get(oid);
    }

    /*package*/ void unexport(int id) {
        exportedObjects.unexport(id);
    }

    /**
     * {@inheritDoc}
     */
    public <V,T extends Throwable>
    V call(Callable<V,T> callable) throws IOException, T, InterruptedException {
        try {
            UserResponse<V,T> r = new UserRequest<V,T>(this, callable).call(this);
            return r.retrieve(this, UserRequest.getClassLoader(callable));

        // re-wrap the exception so that we can capture the stack trace of the caller.
        } catch (ClassNotFoundException e) {
            IOException x = new IOException("Remote call failed");
            x.initCause(e);
            throw x;
        } catch (Error e) {
            IOException x = new IOException("Remote call failed");
            x.initCause(e);
            throw x;
        }
    }

    /**
     * {@inheritDoc}
     */
    public <V,T extends Throwable>
    Future<V> callAsync(final Callable<V,T> callable) throws IOException {
        final Future<UserResponse<V,T>> f = new UserRequest<V,T>(this, callable).callAsync(this);
        return new FutureAdapter<V,UserResponse<V,T>>(f) {
            protected V adapt(UserResponse<V,T> r) throws ExecutionException {
                try {
                    return r.retrieve(Channel.this, UserRequest.getClassLoader(callable));
                } catch (Throwable t) {// really means catch(T t)
                    throw new ExecutionException(t);
                }
            }
        };
    }

    /**
     * Aborts the connection in response to an error.
     */
    protected synchronized void terminate(IOException e) {
        outClosed=inClosed=true;
        try {
            synchronized(pendingCalls) {
                for (Request<?,?> req : pendingCalls.values())
                    req.abort(e);
                pendingCalls.clear();
            }
        } finally {
            notifyAll();

            for (Listener l : listeners.toArray(new Listener[listeners.size()]))
                l.onClosed(this,e);
        }
    }

    /**
     * Registers a new {@link Listener}.
     *
     * @see #removeListener(Listener)
     */
    public void addListener(Listener l) {
        listeners.add(l);
    }

    /**
     * Removes a listener.
     *
     * @return
     *      false if the given listener has not been registered to begin with.
     */
    public boolean removeListener(Listener l) {
        return listeners.remove(l);
    }

    /**
     * Waits for this {@link Channel} to be closed down.
     *
     * The close-down of a {@link Channel} might be initiated locally or remotely.
     *
     * @throws InterruptedException
     *      If the current thread is interrupted while waiting for the completion.
     */
    public synchronized void join() throws InterruptedException {
        while(!inClosed || !outClosed)
            wait();
    }

    /**
     * Notifies the remote peer that we are closing down.
     *
     * Execution of this command also triggers the {@link ReaderThread} to shut down
     * and quit. The {@link CloseCommand} is always the last command to be sent on
     * {@link ObjectOutputStream}, and it's the last command to be read.
     */
    private static final class CloseCommand extends Command {
        protected void execute(Channel channel) {
            try {
                channel.close();
                channel.terminate(null);
            } catch (IOException e) {
                logger.log(Level.SEVERE,"close command failed on "+channel.name,e);
                logger.log(Level.INFO,"close command created at",createdAt);
            }
        }

        public String toString() {
            return "close";
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void close() throws IOException {
        if(outClosed)  return;  // already closed

        send(new CloseCommand());
        outClosed = true;   // last command sent. no further command allowed. lock guarantees that no command will slip inbetween
        try {
            oos.close();
        } catch (IOException e) {
            // there's a race condition here.
            // the remote peer might have already responded to the close command
            // and closed the connection, in which case our close invocation
            // could fail with errors like
            // "java.io.IOException: The pipe is being closed"
            // so let's ignore this error.
        }

        // termination is done by CloseCommand when we received it.
    }

    public String toString() {
        return super.toString()+":"+name;
    }

    private final class ReaderThread extends Thread {
        public ReaderThread(String name) {
            super("Channel reader thread: "+name);
        }

        public void run() {
            Command cmd = null;
            try {
                while(!inClosed) {
                    try {
                        Channel old = Channel.setCurrent(Channel.this);
                        try {
                            cmd = (Command)ois.readObject();
                        } finally {
                            Channel.setCurrent(old);
                        }
                        if(logger.isLoggable(Level.FINE))
                            logger.fine("Received "+cmd);
                        cmd.execute(Channel.this);
                    } catch (ClassNotFoundException e) {
                        logger.log(Level.SEVERE, "Unable to read a command",e);
                    }
                }
                ois.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "I/O error in channel "+name,e);
                terminate(e);
            }
        }
    }

    /*package*/ static Channel setCurrent(Channel channel) {
        Channel old = CURRENT.get();
        CURRENT.set(channel);
        return old;
    }

    /**
     * This method can be invoked during the serialization/deserialization of
     * objects when they are transferred to the remote {@link Channel},
     * as well as during {@link Callable#call()} is invoked. 
     *
     * @return null
     *      if the calling thread is not performing serialization.
     */
    public static Channel current() {
        return CURRENT.get();
    }

    /**
     * Remembers the current "channel" associated for this thread.
     */
    private static final ThreadLocal<Channel> CURRENT = new ThreadLocal<Channel>();

    private static final Logger logger = Logger.getLogger(Channel.class.getName());
}
