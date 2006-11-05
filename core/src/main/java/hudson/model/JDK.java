package hudson.model;

import hudson.EnvVars;

import java.io.File;
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
        String path = env.get("PATH");
        if(path==null)
            path = EnvVars.masterEnvVars.get("PATH");
        
        if(path==null)
            path = getBinDir().getPath();
        else
            path = getBinDir().getPath()+File.pathSeparator+path;
        env.put("PATH",path);
        env.put("JAVA_HOME",javaHome);
        if(!env.containsKey("HUDSON_HOME"))
            env.put("HUDSON_HOME", Hudson.getInstance().getRootDir().getPath() );
    }
}
