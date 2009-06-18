/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.remoting;

import hudson.remoting.ExportTable.ExportList;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.URL;

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
public class Channel implements VirtualChannel, IChannel {
    private final ObjectInputStream ois;
    private final ObjectOutputStream oos;
    private final String name;
    /*package*/ final boolean isRestricted;
    /*package*/ final ExecutorService executor;

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
     * Records the {@link Request}s being executed on this channel, sent by the remote peer.
     */
    /*package*/ final Map<Integer,Request<?,?>> executingCalls =
        Collections.synchronizedMap(new Hashtable<Integer,Request<?,?>>());

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

    /**
     * Total number of nanoseconds spent for remote class loading.
     * <p>
     * Remote code execution often results in classloading activity
     * (more precisely, when the remote peer requests some computation
     * on this channel, this channel often has to load necessary
     * classes from the remote peer.)
     * <p>
     * This counter represents the total amount of time this channel
     * had to spend loading classes from the remote peer. The time
     * measurement doesn't include the time locally spent to actually
     * define the class (as the local classloading would have incurred
     * the same cost.)
     */
    public final AtomicLong classLoadingTime = new AtomicLong();

    /**
     * Total counts of remote classloading activities. Used in a pair
     * with {@link #classLoadingTime}.
     */
    public final AtomicInteger classLoadingCount = new AtomicInteger();

    /**
     * Total number of nanoseconds spent for remote resource loading.
     * @see #classLoadingTime
     */
    public final AtomicLong resourceLoadingTime = new AtomicLong();

    /**
     * Total count of remote resource loading.
     * @see #classLoadingCount
     */
    public final AtomicInteger resourceLoadingCount = new AtomicInteger();

    /**
     * Property bag that contains application-specific stuff.
     */
    private final Hashtable<Object,Object> properties = new Hashtable<Object,Object>();

    /**
     * Proxy to the remote {@link Channel} object.
     */
    private IChannel remoteChannel;

    /**
     * Communication mode.
     * @since 1.161
     */
    public enum Mode {
        /**
         * Send binary data over the stream. Most efficient.
         */
        BINARY(new byte[]{0,0,0,0}),
        /**
         * Send ASCII over the stream. Uses base64, so the efficiency goes down by 33%,
         * but this is useful where stream is binary-unsafe, such as telnet.
         */
        TEXT("<===[HUDSON TRANSMISSION BEGINS]===>") {
            protected OutputStream wrap(OutputStream os) {
                return BinarySafeStream.wrap(os);
            }
            protected InputStream wrap(InputStream is) {
                return BinarySafeStream.wrap(is);
            }
        },
        /**
         * Let the remote peer decide the transmission mode and follow that.
         * Note that if both ends use NEGOTIATE, it will dead lock.
         */
        NEGOTIATE(new byte[0]);

        /**
         * Preamble used to indicate the tranmission mode.
         * Because of the algorithm we use to detect the preamble,
         * the string cannot be any random string. For example,
         * if the preamble is "AAB", we'll fail to find a preamble
         * in "AAAB".
         */
        private final byte[] preamble;

        Mode(String preamble) {
            try {
                this.preamble = preamble.getBytes("US-ASCII");
            } catch (UnsupportedEncodingException e) {
                throw new Error(e);
            }
        }

        Mode(byte[] preamble) {
            this.preamble = preamble;
        }

        protected OutputStream wrap(OutputStream os) { return os; }
        protected InputStream wrap(InputStream is) { return is; }
    }

    public Channel(String name, ExecutorService exec, InputStream is, OutputStream os) throws IOException {
        this(name,exec,Mode.BINARY,is,os,null);
    }

    public Channel(String name, ExecutorService exec, Mode mode, InputStream is, OutputStream os) throws IOException {
        this(name,exec,mode,is,os,null);
    }

    public Channel(String name, ExecutorService exec, InputStream is, OutputStream os, OutputStream header) throws IOException {
        this(name,exec,Mode.BINARY,is,os,header);
    }

    public Channel(String name, ExecutorService exec, Mode mode, InputStream is, OutputStream os, OutputStream header) throws IOException {
        this(name,exec,mode,is,os,header,false);
    }

    /**
     * Creates a new channel.
     * 
     * @param name
     *      Human readable name of this channel. Used for debug/logging. Can be anything.
     * @param exec
     *      Commands sent from the remote peer will be executed by using this {@link Executor}.
     * @param mode
     *      The encoding to be used over the stream.
     * @param is
     *      Stream connected to the remote peer. It's the caller's responsibility to do
     *      buffering on this stream, if that's necessary.
     * @param os
     *      Stream connected to the remote peer. It's the caller's responsibility to do
     *      buffering on this stream, if that's necessary.
     * @param header
     *      If non-null, receive the portion of data in <tt>is</tt> before
     *      the data goes into the "binary mode". This is useful
     *      when the established communication channel might include some data that might
     *      be useful for debugging/trouble-shooting.
     * @param restricted
     *      If true, this channel won't accept {@link Command}s that allow the remote end to execute arbitrary closures
     *      --- instead they can only call methods on objects that are exported by this channel.
     *      This also prevents the remote end from loading classes into JVM.
     *
     *      Note that it still allows the remote end to deserialize arbitrary object graph
     *      (provided that all the classes are already available in this JVM), so exactly how
     *      safe the resulting behavior is is up to discussion.
     */
    public Channel(String name, ExecutorService exec, Mode mode, InputStream is, OutputStream os, OutputStream header, boolean restricted) throws IOException {
        this.name = name;
        this.executor = exec;
        this.isRestricted = restricted;
        ObjectOutputStream oos = null;

        if(export(this,false)!=1)
            throw new AssertionError(); // export number 1 is reserved for the channel itself
        remoteChannel = RemoteInvocationHandler.wrap(this,1,IChannel.class,false);

        // write the magic preamble.
        // certain communication channel, such as forking JVM via ssh,
        // may produce some garbage at the beginning (for example a remote machine
        // might print some warning before the program starts outputting its own data.)
        //
        // so use magic preamble and discard all the data up to that to improve robustness.
        if(mode!= Mode.NEGOTIATE) {
            os.write(mode.preamble);
            oos = new ObjectOutputStream(mode.wrap(os));
            oos.flush();    // make sure that stream preamble is sent to the other end. avoids dead-lock
        }

        {// read the input until we hit preamble
            int[] ptr=new int[2];
            Mode[] modes={Mode.BINARY,Mode.TEXT};

            while(true) {
                int ch = is.read();
                if(ch==-1)
                    throw new EOFException("unexpected stream termination");

                for(int i=0;i<2;i++) {
                    byte[] preamble = modes[i].preamble;
                    if(preamble[ptr[i]]==ch) {
                        if(++ptr[i]==preamble.length) {
                            // found preamble
                            if(mode==Mode.NEGOTIATE) {
                                // now we know what the other side wants, so send the consistent preamble
                                mode = modes[i];
                                os.write(mode.preamble);
                                oos = new ObjectOutputStream(mode.wrap(os));
                                oos.flush();
                            } else {
                                if(modes[i]!=mode)
                                    throw new IOException("Protocol negotiation failure");
                            }
                            this.oos = oos;

                            this.ois = new ObjectInputStream(mode.wrap(is));
                            new ReaderThread(name).start();
                            return;
                        }
                    } else {
                        // didn't match.
                        ptr[i]=0;
                    }
                }

                if(header!=null)
                    header.write(ch);
            }
        }
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

    /*package*/ boolean isOutClosed() {
        return outClosed;
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
        // unless this is the last command, have OOS and remote OIS forget all the objects we sent
        // in this command. Otherwise it'll keep objects in memory unnecessarily.
        // However, this may fail if the command was the close, because that's supposed to be the last command
        // ever sent. See the comment from jglick on HUDSON-3077 about what happens if we do oos.reset(). 
        if(!(cmd instanceof CloseCommand))
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
        return RemoteInvocationHandler.wrap(null,id,type,userProxy);
    }

    /*package*/ int export(Object instance) {
        return exportedObjects.export(instance);
    }

    /*package*/ int export(Object instance, boolean automaticUnexport) {
        return exportedObjects.export(instance,automaticUnexport);
    }

    /*package*/ Object getExportedObject(int oid) {
        return exportedObjects.get(oid);
    }

    /*package*/ void unexport(int id) {
        exportedObjects.unexport(id);
    }

    /**
     * Preloads jar files on the remote side.
     *
     * <p>
     * This is a performance improvement method that can be safely
     * ignored if your goal is just to make things working.
     *
     * <p>
     * Normally, classes are transferred over the network one at a time,
     * on-demand. This design is mainly driven by how Java classloading works
     * &mdash; we can't predict what classes will be necessarily upfront very easily.
     *
     * <p>
     * Classes are loaded only once, so for long-running {@link Channel},
     * this is normally an acceptable overhead. But sometimes, for example
     * when a channel is short-lived, or when you know that you'll need
     * a majority of classes in certain jar files, then it is more efficient
     * to send a whole jar file over the network upfront and thereby
     * avoiding individual class transfer over the network.
     *
     * <p>
     * That is what this method does. It ensures that a series of jar files
     * are copied to the remote side (AKA "preloading.")
     * Classloading will consult the preloaded jars before performing
     * network transfer of class files.
     *
     * @param classLoaderRef
     *      This parameter is used to identify the remote classloader
     *      that will prefetch the specified jar files. That is, prefetching
     *      will ensure that prefetched jars will kick in
     *      when this {@link Callable} object is actually executed remote side.
     *
     *      <p>
     *      {@link RemoteClassLoader}s are created wisely, one per local {@link ClassLoader},
     *      so this parameter doesn't have to be exactly the same {@link Callable}
     *      to be executed later &mdash; it just has to be of the same class.
     * @param classesInJar
     *      {@link Class} objects that identify jar files to be preloaded.
     *      Jar files that contain the specified classes will be preloaded into the remote peer.
     *      You just need to specify one class per one jar.
     * @return
     *      true if the preloading actually happened. false if all the jars
     *      are already preloaded. This method is implemented in such a way that
     *      unnecessary jar file transfer will be avoided, and the return value
     *      will tell you if this optimization kicked in. Under normal circumstances
     *      your program shouldn't depend on this return value. It's just a hint.
     * @throws IOException
     *      if the preloading fails.
     */
    public boolean preloadJar(Callable<?,?> classLoaderRef, Class... classesInJar) throws IOException, InterruptedException {
        return preloadJar(UserRequest.getClassLoader(classLoaderRef),classesInJar);
    }

    public boolean preloadJar(ClassLoader local, Class... classesInJar) throws IOException, InterruptedException {
        URL[] jars = new URL[classesInJar.length];
        for (int i = 0; i < classesInJar.length; i++)
            jars[i] = Which.jarFile(classesInJar[i]).toURI().toURL();
        return call(new PreloadJarTask(jars,local));
    }

    /**
     * {@inheritDoc}
     */
    public <V,T extends Throwable>
    V call(Callable<V,T> callable) throws IOException, T, InterruptedException {
        UserRequest<V,T> request=null;
        try {
            request = new UserRequest<V, T>(this, callable);
            UserResponse<V,T> r = request.call(this);
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
        } finally {
            // since this is synchronous operation, when the round trip is over
            // we assume all the exported objects are out of scope.
            // (that is, the operation shouldn't spawn a new thread or altter
            // global state in the remote system.
            if(request!=null)
                request.releaseExports();
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
            synchronized (executingCalls) {
                for (Request<?, ?> r : executingCalls.values()) {
                    java.util.concurrent.Future<?> f = r.future;
                    if(f!=null) f.cancel(true);
                }
                executingCalls.clear();
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
     * Waits for this {@link Channel} to be closed down, but only up the given milliseconds.
     *
     * @throws InterruptedException
     *      If the current thread is interrupted while waiting for the completion.
     * @since 1.299
     */
    public synchronized void join(long timeout) throws InterruptedException {
        long start = System.currentTimeMillis();
        while(System.currentTimeMillis()-start<timeout && (!inClosed || !outClosed))
            wait(timeout+start-System.currentTimeMillis());
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
                channel.terminate(new OrderlyShutdown(createdAt));
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
     * Signals the orderly shutdown of the channel, but captures
     * where the termination was initiated as a nested exception.
     */
    private static final class OrderlyShutdown extends IOException {
        private OrderlyShutdown(Throwable cause) {
            super(cause.getMessage());
            initCause(cause);
        }
        private static final long serialVersionUID = 1L;
    }

    /**
     * Resets all the performance counters.
     */
    public void resetPerformanceCounters() {
        classLoadingCount.set(0);
        classLoadingTime.set(0);
        resourceLoadingCount.set(0);
        resourceLoadingTime.set(0);
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

    /**
     * Gets the application specific property set by {@link #setProperty(Object, Object)}.
     * These properties are also accessible from the remote channel via {@link #getRemoteProperty(Object)}.
     *
     * <p>
     * This mechanism can be used for one side to discover contextual objects created by the other JVM
     * (as opposed to executing {@link Callable}, which cannot have any reference to the context
     * of the remote {@link Channel}.
     */
    public Object getProperty(Object key) {
        return properties.get(key);
    }

    /**
     * Works like {@link #getProperty(Object)} but wait until some value is set by someone.
     */
    public Object waitForProperty(Object key) throws InterruptedException {
        synchronized (properties) {
            while(true) {
                Object v = properties.get(key);
                if(v!=null) return v;
                properties.wait();
            }
        }
    }

    public Object setProperty(Object key, Object value) {
        synchronized (properties) {
            Object old = properties.put(key, value);
            properties.notifyAll();
            return old;
        }
    }

    public Object getRemoteProperty(Object key) {
        return remoteChannel.getProperty(key);
    }

    public Object waitForRemoteProperty(Object key) throws InterruptedException {
        return remoteChannel.waitForProperty(key);
    }

    public String toString() {
        return super.toString()+":"+name;
    }

    /**
     * Dumps the list of exported objects and their allocation traces to the given output.
     */
    public void dumpExportTable(PrintWriter w) throws IOException {
        exportedObjects.dump(w);
    }

    public ExportList startExportRecording() {
        return exportedObjects.startRecording();
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
                    } catch (ClassNotFoundException e) {
                        logger.log(Level.SEVERE, "Unable to read a command",e);
                    }
                    if(logger.isLoggable(Level.FINE))
                        logger.fine("Received "+cmd);
                    try {
                        cmd.execute(Channel.this);
                    } catch (Throwable t) {
                        logger.log(Level.SEVERE, "Failed to execute command "+cmd,t);
                        logger.log(Level.SEVERE, "This command is created here",cmd.createdAt);
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

//    static {
//        ConsoleHandler h = new ConsoleHandler();
//        h.setFormatter(new Formatter(){
//            public synchronized String format(LogRecord record) {
//                StringBuilder sb = new StringBuilder();
//                sb.append((record.getMillis()%100000)+100000);
//                sb.append(" ");
//                if (record.getSourceClassName() != null) {
//                    sb.append(record.getSourceClassName());
//                } else {
//                    sb.append(record.getLoggerName());
//                }
//                if (record.getSourceMethodName() != null) {
//                    sb.append(" ");
//                    sb.append(record.getSourceMethodName());
//                }
//                sb.append('\n');
//                String message = formatMessage(record);
//                sb.append(record.getLevel().getLocalizedName());
//                sb.append(": ");
//                sb.append(message);
//                sb.append('\n');
//                if (record.getThrown() != null) {
//                    try {
//                        StringWriter sw = new StringWriter();
//                        PrintWriter pw = new PrintWriter(sw);
//                        record.getThrown().printStackTrace(pw);
//                        pw.close();
//                        sb.append(sw.toString());
//                    } catch (Exception ex) {
//                    }
//                }
//                return sb.toString();
//            }
//        });
//        h.setLevel(Level.FINE);
//        logger.addHandler(h);
//        logger.setLevel(Level.FINE);
//    }
}
