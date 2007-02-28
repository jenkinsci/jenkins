package hudson.maven.agent;

import hudson.remoting.Channel;
import hudson.remoting.Launcher;
import junit.framework.TestCase;
import org.codehaus.classworlds.ClassWorld;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * @author Kohsuke Kawaguchi
 */
public class LaunchTest extends TestCase {
    public void test1() throws Throwable {
        List<String> args = new ArrayList<String>();
        args.add("java");
        
        //args.add("-Xrunjdwp:transport=dt_socket,server=y,address=8000");

        args.add("-cp");
        args.add(toJarFile(Main.class)+File.pathSeparator+toJarFile(ClassWorld.class));
        args.add(Main.class.getName());

        // M2_HOME
        args.add("c:\\development\\Java\\maven2");
        // remoting.jar
        args.add(toJarFile(Launcher.class).getPath());
        // interceptor.jar
        args.add(toJarFile(PluginManagerInterceptor.class).getPath());

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
    }

    /**
     * Finds the jar file that contains the class.
     */
    private File toJarFile(Class clazz) throws IOException, URISyntaxException {
        String res = clazz.getClassLoader().getResource(clazz.getName().replace('.', '/') + ".class").toExternalForm();
        if(res.startsWith("jar:")) {
            res = res.substring(4,res.lastIndexOf('!')); // cut off jar: and the file name portion
            return new File(decode(new URL(res).getPath()));
        }

        if(res.startsWith("file:")) {
            // unpackaged classes
            int n = clazz.getName().split("\\.").length; // how many slashes do wo need to cut?
            for( ; n>0; n-- ) {
                int idx = Math.max(res.lastIndexOf('/'), res.lastIndexOf('\\'));
                res = res.substring(0,idx);
            }

            // won't work if res URL contains ' '
            // return new File(new URI(null,new URL(res).toExternalForm(),null));
            // won't work if res URL contains '%20'
            // return new File(new URL(res).toURI());

            return new File(decode(new URL(res).getPath()));
        }

        throw new IllegalArgumentException(res);
    }

    /**
     * Decode '%HH'.
     */
    private static String decode(String s) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for( int i=0; i<s.length();i++ ) {
            char ch = s.charAt(i);
            if(ch=='%') {
                baos.write(hexToInt(s.charAt(i+1))*16 + hexToInt(s.charAt(i+2)));
                i+=2;
                continue;
            }
            baos.write(ch);
        }
        try {
            return new String(baos.toByteArray(),"UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error(e); // impossible
        }
    }

    private static int hexToInt(int ch) {
        return Character.getNumericValue(ch);
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while((len=in.read(buf))>0)
            out.write(buf,0,len);
    }
}
