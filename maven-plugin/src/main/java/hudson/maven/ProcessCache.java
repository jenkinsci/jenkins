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
package hudson.maven;

import hudson.Util;
import hudson.Proc;
import hudson.model.BuildListener;
import hudson.model.JDK;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.remoting.RequestAbortedException;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.DelegatingOutputStream;
import hudson.util.NullStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Hold on to launched Maven processes so that multiple builds
 * can reuse the same Maven JVM, which leads to improved performance.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ProcessCache {
    /**
     * Implemented by the caller to create a new process
     * (when a new one is needed.)
     */
    interface Factory {
        /**
         * @param out
         *      The output from the process should be sent to this output stream.
         */
        NewProcess newProcess(BuildListener listener,OutputStream out) throws IOException, InterruptedException;
        String getMavenOpts();
        MavenInstallation getMavenInstallation(TaskListener listener) throws IOException, InterruptedException;
        JDK getJava(TaskListener listener) throws IOException, InterruptedException;
    }

    public static class NewProcess {
        public final Channel channel;
        public final Proc proc;

        public NewProcess(Channel channel, Proc proc) {
            this.channel = channel;
            this.proc = proc;
        }
    }

    class MavenProcess {
        /**
         * Channel connected to the maven process.
         */
        final Channel channel;
        /**
         * MAVEN_OPTS of this VM.
         */
        private final String mavenOpts;
        private final PerChannel parent;
        final Proc proc;
        private final MavenInstallation installation;
        private final JDK jdk;
        private final RedirectableOutputStream output;
        /**
         * System properties captured right after the process is created.
         * Each time the process is reused, the system properties are reset,
         * since Maven corrupts them as a side-effect of the build.
         */
        private final Properties systemProperties;

        private int age = 0;

        MavenProcess(PerChannel parent, String mavenOpts, MavenInstallation installation, JDK jdk, NewProcess np, RedirectableOutputStream output) throws IOException, InterruptedException {
            this.parent = parent;
            this.mavenOpts = mavenOpts;
            this.channel = np.channel;
            /* Probably not currently useful for testing; could add if we did not want plugins accidentally loading 7+ bytecode into Maven JVM:
            short javaLevel = 6; // cf. jenkins/pom.xml#java.level
            channel.setMaximumBytecodeLevel(javaLevel);
            */
            this.proc = np.proc;
            this.installation = installation;
            this.jdk = jdk;
            this.output = output;
            this.systemProperties = channel.call(new GetSystemProperties());
        }

        public String getMavenOpts() {
            return mavenOpts;
        }
        
        boolean matches(String mavenOpts,MavenInstallation installation, JDK jdk) {
            return Util.fixNull(this.mavenOpts).equals(Util.fixNull(mavenOpts))
                && this.installation==installation
                && this.jdk==jdk;
        }

        public void recycle() throws IOException {
            if(age>=MAX_AGE || maxProcess==0)
                discard();
            else {
                output.set(new NullStream());
                // make room for the new process and reuse.
                synchronized(parent.processes) {
                    while(parent.processes.size()>=maxProcess)
                        parent.processes.removeFirst().discard();
                    parent.processes.add(this);
                }
            }
        }

        /**
         * Discards this maven process.
         * It won't be reused in future builds.
         */
        public void discard() {
            try {
                channel.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,"Failed to discard the maven process orderly",e);
            }
        }

        /**
         * Calls a {@link Callable} on the channel, with additional error diagnostics.
         */
        public <V,T extends Throwable> V call(Callable<V,T> callable) throws T, IOException, InterruptedException {
            try {
                return channel.call(callable);
            } catch (RequestAbortedException e) {
                // this is normally triggered by the unexpected Maven JVM termination.
                // check if the process is still alive, after giving it a bit of time to die
                Thread.sleep(1000);
                if(proc.isAlive())
                    throw e; // it's still alive. treat this as a bug in the code
                else {
                    String msg = "Maven JVM terminated unexpectedly with exit code " + proc.join();
                    LOGGER.log(Level.FINE,msg,e);
                    throw new hudson.AbortException(msg);
                }
            }
        }
    }

    static class PerChannel {
        /**
         * Cached processes.
         */
        private final LinkedList<MavenProcess> processes = new LinkedList<MavenProcess>();
    }

    // use WeakHashMap to avoid keeping VirtualChannel in memory.
    private final Map<VirtualChannel,PerChannel> cache = new WeakHashMap<VirtualChannel,PerChannel>();
    private final int maxProcess;

    /**
     * @param maxProcess
     *      Number of maximum processes to cache.
     */
    protected ProcessCache(int maxProcess) {
        this.maxProcess = maxProcess;
    }

    private synchronized PerChannel get(VirtualChannel owner) {
        PerChannel r = cache.get(owner);
        if(r==null)
            cache.put(owner,r=new PerChannel());
        return r;
    }

    /**
     * Gets or creates a new maven process for launch.
     */
    public MavenProcess get(VirtualChannel owner, BuildListener listener, Factory factory) throws InterruptedException, IOException {
        String mavenOpts = factory.getMavenOpts();
        MavenInstallation installation = factory.getMavenInstallation(listener);
        JDK jdk = factory.getJava(listener);

        PerChannel list = get(owner);
        synchronized(list.processes) {
            for (Iterator<MavenProcess> itr = list.processes.iterator(); itr.hasNext();) {
                MavenProcess p =  itr.next();
                if(p.matches(mavenOpts,installation,jdk)) {
                    // reset the system property.
                    // this also serves as the sanity check.
                    try {
                        p.call(new SetSystemProperties(p.systemProperties));
                    } catch (IOException e) {
                        p.discard();
                        itr.remove();
                        continue;
                    }

                    listener.getLogger().println(Messages.ProcessCache_Reusing());
                    itr.remove();
                    p.age++;
                    p.output.set(listener.getLogger());
                    return p;
                }
            }
        }

        RedirectableOutputStream out = new RedirectableOutputStream(listener.getLogger());
        return new MavenProcess(list,mavenOpts,installation,jdk,factory.newProcess(listener,out),out);
    }



    public static int MAX_AGE = 5;

    static {
        String age = System.getProperty(ProcessCache.class.getName() + ".age");
        if(age!=null)
            MAX_AGE = Integer.parseInt(age);
    }

    /**
     * Noop callable used for checking the sanity of the maven process in the cache.
     */
    private static class SetSystemProperties implements Callable<Object,RuntimeException> {
        private final Properties properties;

        public SetSystemProperties(Properties properties) {
            this.properties = properties;
        }

        public Object call() {
            System.setProperties(properties);
            return null;
        }
        private static final long serialVersionUID = 1L;
    }

    private static class GetSystemProperties implements Callable<Properties,RuntimeException> {
        public Properties call() {
            return System.getProperties();
        }
        private static final long serialVersionUID = 1L;
    }

    static class RedirectableOutputStream extends DelegatingOutputStream {
        public RedirectableOutputStream(OutputStream out) {
            super(out);
        }

        public void set(OutputStream os) {
            super.out = os;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ProcessCache.class.getName());
}
