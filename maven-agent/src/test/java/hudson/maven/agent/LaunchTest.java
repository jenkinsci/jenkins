package hudson.maven.agent;

import hudson.remoting.Channel;
import hudson.remoting.Launcher;
import hudson.remoting.Which;
import junit.framework.TestCase;
import org.codehaus.classworlds.ClassWorld;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * @author Kohsuke Kawaguchi
 */
public class LaunchTest extends TestCase {
    public void test1() throws Throwable {
/*
        List<String> args = new ArrayList<String>();
        args.add("java");
        
        args.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8002");

        System.out.println(Channel.class);

        args.add("-cp");
        args.add(Which.jarFile(Main.class)+File.pathSeparator+Which.jarFile(ClassWorld.class));
        args.add(Main.class.getName());

        // M2_HOME
        args.add(System.getProperty("maven.home"));
        // remoting.jar
        args.add(Which.jarFile(Launcher.class).getPath());
        // interceptor.jar
        args.add(Which.jarFile(PluginManagerInterceptor.class).getPath());

        System.out.println("Launching "+args);

        final Process proc = Runtime.getRuntime().exec(args.toArray(new String[0]));

        // start copying system err
        new Thread() {
            public void run() {
                try {
                    copyStream(proc.getErrorStream(),System.err);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();

        Channel ch = new Channel("maven", Executors.newCachedThreadPool(),
            proc.getInputStream(), proc.getOutputStream(), System.err);

        System.out.println("exit code="+ch.call(new RunCommand("help:effective-settings")));

        ch.close();

        System.out.println("done");
*/
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while((len=in.read(buf))>0)
            out.write(buf,0,len);
    }
}
