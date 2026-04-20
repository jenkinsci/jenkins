/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, CloudBees, Inc.
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

package hudson.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.FilePath;
import hudson.Functions;
import hudson.Util;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.remoting.AsyncFutureImpl;
import hudson.remoting.DelegatingCallable;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import hudson.security.AccessControlled;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import jenkins.util.ScriptListener;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Various remoting operations related to diagnostics.
 *
 * <p>
 * These code are useful wherever {@link VirtualChannel} is used, such as master, agents, Maven JVMs, etc.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.175
 */
public final class RemotingDiagnostics {
    public static Map<Object, Object> getSystemProperties(VirtualChannel channel) throws IOException, InterruptedException {
        if (channel == null)
            return Map.of("N/A", "N/A");
        return channel.call(new GetSystemProperties());
    }

    private static final class GetSystemProperties extends MasterToSlaveCallable<Map<Object, Object>, RuntimeException> {
        @Override
        public Map<Object, Object> call() {
            return new TreeMap<>(System.getProperties());
        }

        private static final long serialVersionUID = 1L;
    }

    public static Map<String, String> getThreadDump(VirtualChannel channel) throws IOException, InterruptedException {
        if (channel == null)
            return Map.of("N/A", "N/A");
        return channel.call(new GetThreadDump());
    }

    public static Future<Map<String, String>> getThreadDumpAsync(VirtualChannel channel) throws IOException, InterruptedException {
        if (channel == null)
            return new AsyncFutureImpl<>(Map.of("N/A", "offline"));
        return channel.callAsync(new GetThreadDump());
    }

    private static final class GetThreadDump extends MasterToSlaveCallable<Map<String, String>, RuntimeException> {
        @Override
        public Map<String, String> call() {
            Map<String, String> r = new LinkedHashMap<>();
                ThreadInfo[] data = Functions.getThreadInfos();
                Functions.ThreadGroupMap map = Functions.sortThreadsAndGetGroupMap(data);
                for (ThreadInfo ti : data)
                    r.put(ti.getThreadName(), Functions.dumpThreadInfo(ti, map));
            return r;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Executes Groovy script remotely.
     */
    public static String executeGroovy(String script, @NonNull VirtualChannel channel) throws IOException, InterruptedException {
        final String correlationId = UUID.randomUUID().toString();
        final String context = channel.toString();
        ScriptListener.fireScriptExecution(script, new Binding(), RemotingDiagnostics.class, context, correlationId, User.current());
        final String output = channel.call(new Script(script));
        ScriptListener.fireScriptOutput(output, RemotingDiagnostics.class, context, correlationId, User.current());
        return output;
    }

    private static final class Script extends MasterToSlaveCallable<String, RuntimeException> implements DelegatingCallable<String, RuntimeException> {
        private final String script;
        private transient ClassLoader cl;

        private Script(String script) {
            this.script = script;
            cl = getClassLoader();
        }

        @Override
        public ClassLoader getClassLoader() {
            return Jenkins.get().getPluginManager().uberClassLoader;
        }

        @Override
        public String call() throws RuntimeException {
            // if we run locally, cl!=null. Otherwise the delegating classloader will be available as context classloader.
            if (cl == null)       cl = Thread.currentThread().getContextClassLoader();
            CompilerConfiguration cc = new CompilerConfiguration();
            cc.addCompilationCustomizers(new ImportCustomizer().addStarImports(
                    "jenkins",
                    "jenkins.model",
                    "hudson",
                    "hudson.model"));
            GroovyShell shell = new GroovyShell(cl, new Binding(), cc);

            StringWriter out = new StringWriter();
            PrintWriter pw = new PrintWriter(out);
            shell.setVariable("out", pw);
            try {
                Object output = evaluateScript(shell);
                if (output != null)
                pw.println("Result: " + output);
            } catch (Throwable t) {
                Functions.printStackTrace(t, pw);
            }
            return out.toString();
        }

        @SuppressFBWarnings(value = "GROOVY_SHELL", justification = "script console is a feature, not a bug")
        private Object evaluateScript(GroovyShell shell) {
            return shell.evaluate(script);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Obtains the heap dump in an HPROF file.
     */
    public static FilePath getHeapDump(VirtualChannel channel) throws IOException, InterruptedException {
        return channel.call(new GetHeapDump());
    }

    private static class GetHeapDump extends MasterToSlaveCallable<FilePath, IOException> {
            @Override
            public FilePath call() throws IOException {
                final File hprof = File.createTempFile("hudson-heapdump", ".hprof");
                Files.delete(Util.fileToPath(hprof));
                try {
                    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                    server.invoke(new ObjectName("com.sun.management:type=HotSpotDiagnostic"), "dumpHeap",
                            new Object[]{hprof.getAbsolutePath(), true}, new String[]{String.class.getName(), boolean.class.getName()});

                    return new FilePath(hprof);
                } catch (JMException e) {
                    throw new IOException(e);
                }
            }

            private static final long serialVersionUID = 1L;
    }

    /**
     * Heap dump, exposable to URL via Stapler.
     *
     */
    public static class HeapDump implements ModelObject {
        private final AccessControlled owner;
        private final VirtualChannel channel;

        public HeapDump(AccessControlled owner, VirtualChannel channel) {
            this.owner = owner;
            this.channel = channel;
        }

        @WebMethod(name = "heapdump.hprof")
        @RequirePOST
        public void doHeapDump(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, InterruptedException {
            owner.checkPermission(Jenkins.ADMINISTER);
            rsp.setContentType("application/octet-stream");

            FilePath dump = obtain();
            try {
                dump.copyTo(rsp.getOutputStream());
            } finally {
                dump.delete();
            }
        }

        @Restricted(DoNotUse.class)
        public AccessControlled getContext() {
            if (owner instanceof ModelObject) {
                return owner;
            }
            return Jenkins.get();
        }

        public FilePath obtain() throws IOException, InterruptedException {
            return RemotingDiagnostics.getHeapDump(channel);
        }

        @Override
        public String getDisplayName() {
            return Messages.HeapDump_DisplayName();
        }
    }
}
