package hudson.model;

import hudson.util.StreamTaskListener;
import hudson.util.NullStream;
import hudson.Launcher;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Information about JDK installation.
 *
 * @author Kohsuke Kawaguchi
 */
public final class JDK {
    private final String name;
    private final String javaHome;

    public JDK(String name, String javaHome) {
        this.name = name;
        this.javaHome = javaHome;
    }

    /**
     * install directory.
     */
    public String getJavaHome() {
        return javaHome;
    }

    /**
     * Human readable display name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the path to the bin directory.
     */
    public File getBinDir() {
        return new File(getJavaHome(),"bin");
    }
    /**
     * Gets the path to 'java'.
     */
    private File getExecutable() {
        String execName;
        if(File.separatorChar=='\\')
            execName = "java.exe";
        else
            execName = "java";

        return new File(getJavaHome(),"bin/"+execName);
    }

    /**
     * Returns true if the executable exists.
     */
    public boolean getExists() {
        return getExecutable().exists();
    }

    /**
     * Sets PATH and JAVA_HOME from this JDK.
     */
    public void buildEnvVars(Map<String,String> env) {
        // see EnvVars javadoc for why this adss PATH.
        env.put("PATH+JDK",getBinDir().getPath());

        env.put("JAVA_HOME",javaHome);
        if(!env.containsKey("HUDSON_HOME"))
            env.put("HUDSON_HOME", Hudson.getInstance().getRootDir().getPath() );
    }

    /**
     * Checks if "java" is in PATH on the given node.
     *
     * <p>
     * If it's not, then the user must specify a configured JDK,
     * so this is often useful for form field validation.
     */
    public static boolean isDefaultJDKValid(Node n) {
        try {
            TaskListener listener = new StreamTaskListener(new NullStream());
            Launcher launcher = n.createLauncher(listener);
            return launcher.launch("java -fullversion",new String[0],listener.getLogger(),null).join()==0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            return false;
        }
    }
}
