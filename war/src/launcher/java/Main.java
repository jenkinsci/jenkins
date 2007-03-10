import java.net.URL;
import java.net.URLClassLoader;
import java.net.URISyntaxException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Launcher class for stand-alone execution of Hudson as
 * <tt>java -jar hudson.war</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
public class Main {
    public static void main(String[] args) throws Exception {
        File me = whoAmI();

        // locate Winstone jar
        URL jar = Main.class.getResource("winstone.jar");

        // put this jar in a file system so that we can load jars from there
        File tmpJar = File.createTempFile("winstone", "jar");
        copyStream(jar.openStream(), new FileOutputStream(tmpJar));
        tmpJar.deleteOnExit();

        // clean up any previously extracted copy, since
        // winstone doesn't do so and that causes problems when newer version of Hudson
        // is deployed.
        File tempFile = File.createTempFile("dummy", "dummy");
        deleteContents(new File(tempFile.getParent(), "winstone/" + me.getName()));
        tempFile.delete();

        // locate the Winstone launcher
        ClassLoader cl = new URLClassLoader(new URL[]{tmpJar.toURL()});
        Class launcher = cl.loadClass("winstone.Launcher");
        Method mainMethod = launcher.getMethod("main", new Class[]{String[].class});

        // figure out the arguments
        List arguments = new ArrayList(Arrays.asList(args));
        arguments.add(0,"--warfile="+ me.getAbsolutePath());

        // run
        mainMethod.invoke(null,new Object[]{arguments.toArray(new String[0])});
    }

    /**
     * Figures out the URL of <tt>hudson.war</tt>.
     */
    public static File whoAmI() throws IOException, URISyntaxException {
        URL classFile = Main.class.getClassLoader().getResource("Main.class");
        String loc = classFile.toExternalForm().substring(4);// cut off jar:
        loc = loc.substring(0,loc.lastIndexOf('!'));

        // assume 'loc' is a file URL and return the file name.
        // toURI needed to handle %20 in URL.
        return new File(new URL(loc).toURI().getPath());
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while((len=in.read(buf))>0)
            out.write(buf,0,len);
        in.close();
        out.close();
    }

    private static void deleteContents(File file) throws IOException {
        if(file.isDirectory()) {
            File[] files = file.listFiles();
            if(files!=null) {// be defensive
                for (int i = 0; i < files.length; i++)
                    deleteContents(files[i]);
            }
        }
        file.delete();
    }
}
