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
