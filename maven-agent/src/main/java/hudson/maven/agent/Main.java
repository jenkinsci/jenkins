package hudson.maven.agent;

import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.Launcher;
import org.codehaus.classworlds.NoSuchRealmException;
import org.codehaus.classworlds.DefaultClassRealm;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;

/**
 * Entry point for launching Maven and Hudson remoting in the same VM,
 * in the classloader layout that Maven expects.
 *
 * <p>
 * The actual Maven execution will be started by the program sent
 * through remoting. 
 *
 * @author Kohsuke Kawaguchi
 */
public class Main {
    /**
     * Used to pass the classworld instance to the code running inside the remoting system.
     */
    private static Launcher launcher;

    public static void main(String[] args) throws Exception {
        main(new File(args[0]),new File(args[1]),new File(args[2]));
    }

    public static void main(File m2Home, File remotingJar, File interceptorJar) throws Exception {
        System.setProperty("maven.home",m2Home.getPath());
        System.setProperty("maven.interceptor",interceptorJar.getPath());

        boolean is206OrLater = !new File(m2Home,"core").exists();

        // load the default realms
        launcher = new Launcher();
        launcher.setSystemClassLoader(Main.class.getClassLoader());
        launcher.configure(Main.class.getResourceAsStream(
            is206OrLater?"classworlds-2.0.6.conf":"classworlds.conf"));

        // have it eventually delegate to this class so that this can be visible

        // create a realm for loading remoting subsystem.
        // this needs to be able to see maven.
        ClassRealm remoting = new DefaultClassRealm(launcher.getWorld(),"hudson-remoting", launcher.getSystemClassLoader());
        remoting.setParent(launcher.getWorld().getRealm("plexus.core.maven"));
        remoting.addConstituent(remotingJar.toURL());

        // we'll use stdin/out to talk to the host,
        // so make sure Maven won't touch them later
        OutputStream os = System.out;
        System.setOut(System.err);
        InputStream is = System.in;
        System.setIn(new ByteArrayInputStream(new byte[0]));

        Class remotingLauncher = remoting.loadClass("hudson.remoting.Launcher");
        remotingLauncher.getMethod("main",new Class[]{InputStream.class,OutputStream.class}).invoke(null,new Object[]{is,os});
        System.exit(0);
    }

    /**
     * Called by the code in remoting to launch.
     */
    public static int launch(String[] args) throws NoSuchMethodException, IllegalAccessException, NoSuchRealmException, InvocationTargetException, ClassNotFoundException {
        launcher.launch(args);
        return launcher.getExitCode();
    }
}