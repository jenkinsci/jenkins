package hudson.util;

import groovy.lang.GroovyShell;
import hudson.Functions;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ThreadInfo;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Various remoting operations related to diagnostics.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.175
 */
public final class RemotingDiagnostics {
    public static Map<Object,Object> getSystemProperties(VirtualChannel channel) throws IOException, InterruptedException {
        if(channel==null)
            return Collections.<Object,Object>singletonMap("N/A","N/A");
        return channel.call(new GetSystemProperties());
    }

    private static final class GetSystemProperties implements Callable<Map<Object,Object>,RuntimeException> {
        public Map<Object,Object> call() {
            return new TreeMap<Object,Object>(System.getProperties());
        }
        private static final long serialVersionUID = 1L;
    }

    public static Map<String,String> getThreadDump(VirtualChannel channel) throws IOException, InterruptedException {
        if(channel==null)
            return Collections.singletonMap("N/A","N/A");
        return channel.call(new GetThreadDump());
    }

    private static final class GetThreadDump implements Callable<Map<String,String>,RuntimeException> {
        public Map<String,String> call() {
            Map<String,String> r = new LinkedHashMap<String,String>();
            try {
                for (ThreadInfo ti : Functions.getThreadInfos())
                    r.put(ti.getThreadName(),Functions.dumpThreadInfo(ti));
            } catch (LinkageError _) {
                // not in JDK6. fall back to JDK5
                r.clear();
                for (Map.Entry<Thread,StackTraceElement[]> t : Thread.getAllStackTraces().entrySet()) {
                    StringBuffer buf = new StringBuffer();
                    for (StackTraceElement e : t.getValue())
                        buf.append(e).append('\n');
                    r.put(t.getKey().getName(),buf.toString());
                }
            }
            return r;
        }
        private static final long serialVersionUID = 1L;
    }

    /**
     * Executes Groovy script remotely.
     */
    public static String executeGroovy(String script, VirtualChannel channel) throws IOException, InterruptedException {
        return channel.call(new Script(script));
    }

    private static final class Script implements Callable<String,RuntimeException> {
        private final String script;

        private Script(String script) {
            this.script = script;
        }

        public String call() throws RuntimeException {
            GroovyShell shell = new GroovyShell();

            StringWriter out = new StringWriter();
            PrintWriter pw = new PrintWriter(out);
            shell.setVariable("out", pw);
            try {
                Object output = shell.evaluate(script);
                if(output!=null)
                pw.println("Result: "+output);
            } catch (Throwable t) {
                t.printStackTrace(pw);
            }
            return out.toString();
        }
    }
}
