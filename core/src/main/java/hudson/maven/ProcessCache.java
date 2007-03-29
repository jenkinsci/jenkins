package hudson.maven;

import hudson.Util;
import hudson.tasks.Maven.MavenInstallation;
import hudson.model.BuildListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hold on to launched Maven processes so that multiple builds
 * can reuse the same Maven JVM, which leads to improved performance.
 *
 * @author Kohsuke Kawaguchi
 */
final class ProcessCache {
    interface Factory {
        Channel newProcess(BuildListener listener) throws IOException, InterruptedException;
        String getMavenOpts();
        MavenInstallation getMavenInstallation();
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
        private final MavenInstallation installation;

        private int age = 0;

        MavenProcess(PerChannel parent, String mavenOpts, MavenInstallation installation, Channel channel) {
            this.parent = parent;
            this.mavenOpts = mavenOpts;
            this.channel = channel;
            this.installation = installation;
        }

        boolean matches(String mavenOpts,MavenInstallation installation) {
            return Util.fixNull(this.mavenOpts).equals(Util.fixNull(mavenOpts)) && this.installation==installation;
        }

        public void recycle() throws IOException {
            if(age>=MAX_AGE || maxProcess==0)
                discard();
            else {
                // make room for the new process and reuse.
                synchronized(parent.processes) {
                    while(parent.processes.size()>=maxProcess)
                        parent.processes.removeFirst().discard();
                    parent.processes.add(this);
                }
            }
        }

        public void discard() throws IOException {
            channel.close();
        }
    }

    class PerChannel {
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
        MavenInstallation installation = factory.getMavenInstallation();

        PerChannel list = get(owner);
        synchronized(list) {
            for (Iterator<MavenProcess> itr = list.processes.iterator(); itr.hasNext();) {
                MavenProcess p =  itr.next();
                if(p.matches(mavenOpts, installation)) {
                    try {// quickly check if this process is still alive
                        p.channel.call(new Noop());
                    } catch (IOException e) {
                        p.discard();
                        itr.remove();
                        continue;
                    }

                    listener.getLogger().println("Reusing existing maven process");
                    itr.remove();
                    p.age++;
                    return p;
                }
            }
        }

        return new MavenProcess(list,mavenOpts,installation,factory.newProcess(listener));
    }



    public static int MAX_AGE = 5;

    private static class Noop implements Callable<Object,RuntimeException>, Serializable {
        public Object call() {
            return null;
        }
        private static final long serialVersionUID = 1L;
    }
}
