package hudson.remoting;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
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

    /**
     * If true, this data channel is already closed and
     * no further calls are accepted.
     */
    private boolean closed = false;

    /*package*/ final Map<Integer,Request<?,?>> pendingCalls = new Hashtable<Integer,Request<?,?>>();

    /**
     * {@link ClassLoader}s that are proxies of the remote classloaders.
     */
    /*package*/ final ImportedClassLoaderTable importedClassLoaders = new ImportedClassLoaderTable(this);

    /**
     * Objects exported via {@link #export(Class, Object)}.
     */
    private final ExportTable<Object> exportedObjects = new ExportTable<Object>();

    public Channel(String name, Executor exec, InputStream is, OutputStream os) throws IOException {
        this(name,exec,is,os,null);
    }

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
     * @param header
     *      If non-null, receive the portion of data in <tt>is</tt> before
     *      the data goes into the "binary mode". This is useful
     *      when the established communication channel might include some data that might
     *      be useful for debugging/trouble-shooting.
     */
    public Channel(String name, Executor exec, InputStream is, OutputStream os, OutputStream header) throws IOException {
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
     * Sends a command to the remote end and executes it there.
     *
     * <p>
     * This is the lowest layer of abstraction in {@link Channel}.
     * {@link Command}s are executed on a remote system in the order they are sent.
     */
    /*package*/ synchronized void send(Command cmd) throws IOException {
        if(closed)
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
     * Exports an object for remoting to the other {@link Channel}.
     *
     * @param type
     *      Interface to be remoted.
     * @return
     *      the proxy object that implements <tt>T</tt>. This object can be transfered
     *      to the other {@link Channel}, and calling methods on it will invoke the
     *      same method on the given <tt>instance</tt> object.
     */
    /*package*/ synchronized <T> T export(Class<T> type, T instance) {
        if(instance==null)
            return null;
        // TODO: unexport

        final int id = export(instance);
        return type.cast(Proxy.newProxyInstance( type.getClassLoader(), new Class[]{type},
            new RemoteInvocationHandler(id)));
    }

    /*package*/ int export(Object instance) {
        return exportedObjects.intern(instance);
    }

    /*package*/ Object getExportedObject(int oid) {
        return exportedObjects.get(oid);
    }

    /**
     * Makes a remote procedure call.
     *
     * <p>
     * Sends {@link Callable} to the remote system, executes it, and returns its result.
     *
     * @throws InterruptedException
     *      If the current thread is interrupted while waiting for the completion.
     * @throws IOException
     *      If there's any error in the communication between {@link Channel}s.
     */
    public <V extends Serializable,T extends Throwable>
    V call(Callable<V,T> callable) throws IOException, T, InterruptedException {
        UserResponse<V> r = new UserRequest<V,T>(this, callable).call(this);
        try {
            return r.retrieve(this, callable.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            // this is unlikely to happen, so this is a lame implementation
            IOException x = new IOException();
            x.initCause(e);
            throw x;
        }
    }

    /**
     * Makes an asynchronous remote procedure call.
     *
     * <p>
     * Similar to {@link #call(Callable)} but returns immediately.
     * The result of the {@link Callable} can be obtained through the {@link Future} object.
     */
    public <V extends Serializable,T extends Throwable>
    Future<V> callAsync(final Callable<V,T> callable) throws IOException, T, InterruptedException {
        final Future<UserResponse<V>> f = new UserRequest<V, T>(this, callable).callAsync(this);
        return new FutureAdapter<V,UserResponse<V>>(f) {
            protected V adapt(UserResponse<V> r) throws ExecutionException {
                try {
                    return r.retrieve(Channel.this,callable.getClass().getClassLoader());
                } catch (IOException e) {
                    throw new ExecutionException(e);
                } catch (ClassNotFoundException e) {
                    throw new ExecutionException(e);
                }
            }
        };
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
     * Waits for this {@link Channel} to be closed down.
     *
     * The close-down of a {@link Channel} might be initiated locally or remotely.
     */
    public synchronized void join() throws InterruptedException {
        while(!closed)
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
    public synchronized void close() throws IOException {
        // make sure no other commands get executed in between.
        if(closed)  return;

        send(new CloseCommand());
        oos.close();

        // TODO: would be nice if we can wait for the completion of pending requests
        terminate(null);
    }

    private final class ReaderThread extends Thread {
        public ReaderThread(String name) {
            super("Channel reader thread: "+name);
        }

        public void run() {
            try {
                while(!closed) {
                    try {
                        Command cmd = null;
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
                        logger.log(Level.SEVERE, "Unabled to read a command",e);
                    }
                }
                ois.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "I/O error in channel",e);
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
     * objects, when they are transferred to the remote {@link Channel}.
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
