package hudson.util;

import hudson.model.Run;
import hudson.Util;
import hudson.EnvVars;
import org.jvnet.winp.WinProcess;
import org.jvnet.winp.WinpException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/**
 * Kills a process tree to clean up the mess left by a build.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.201
 */
public abstract class ProcessTreeKiller {
    /**
     * Kills the given process (like {@link Process#destroy()}
     * but also attempts to kill descendant processes created from the given
     * process.
     *
     * <p>
     * The implementation is obviously OS-dependent.
     *
     * <p>
     * The execution doesn't have to be blocking; the method may return
     * before processes are actually killed.
     */
    public abstract void kill(Process proc);

    /**
     * In addition to what {@link #kill(Process)} does, also tries to
     * kill all the daemon processes launched.
     *
     * <p>
     * Several different strategies are possible to determine what
     * daemon processes are launched from the build, but the recommended
     * approach is to check the environment variable JOB_NAME
     * and BUILD_NUMBER. See {@link Run#getEnvVars()} for more details.
     */
    public abstract void kill(Process proc, Run<?,?> run);

    protected boolean hasMatchingEnvVars(Map<String,String> envVar, Map<String,String> modelEnvVar) {
        String n = modelEnvVar.get("JOB_NAME");
        if(!n.equals(envVar.get("JOB_NAME")))   return false;

        String job = modelEnvVar.get("BUILD_NUMER");
        return job.equals(envVar.get("BUILD_NUMER"));
    }

    /**
     * Gets the {@link ProcessTreeKiller} suitable for the current system
     * that JVM runs in, or in the worst case return the default one
     * that's not capable of killing descendants at all.
     */
    public static ProcessTreeKiller get() {
        if(File.pathSeparatorChar==';')
            return new Windows();

        String os = Util.fixNull(System.getProperty("os.name"));
        if(os.equals("Linux"))
            return new Linux();

        return DEFAULT;
    }

    /**
     * Fallback implementation that doesn't do anything clever.
     */
    private static final ProcessTreeKiller DEFAULT = new ProcessTreeKiller() {
        public void kill(Process proc) {
            proc.destroy();
        }

        public void kill(Process proc, Run<?,?> run) {
            proc.destroy();
        }
    };

    /**
     * Implementation for Windows.
     *
     * <p>
     * Not a singleton pattern because loading this class requires Windows specific library.
     */
    private static final class Windows extends ProcessTreeKiller {
        public void kill(Process proc) {
            new WinProcess(proc).killRecursively();
        }

        public void kill(Process proc, Run<?,?> run) {
            kill(proc);
            Map<String,String> modelEnvVars = run.getEnvVars();

            for( WinProcess p : WinProcess.all() ) {
                if(p.getPid()<10)
                    continue;   // ignore system processes like "idle process"

                boolean matched;
                try {
                    matched = hasMatchingEnvVars(p.getEnvironmentVariables(), modelEnvVars);
                } catch (WinpException e) {
                    // likely a missing privilege
                    continue;
                }

                if(matched)
                    p.killRecursively();
            }
        }

        static {
            WinProcess.enableDebugPrivilege();
        }
    }

    /**
     * Implementation for Linux that uses <tt>/proc</tt>.
     */
    private static final class Linux extends ProcessTreeKiller {
        /**
         * Represents a single Linux system, which hosts multiple processes.
         *
         * <p>
         * The object represents a snapshot of the system state.  
         */
        static class LinuxSystem implements Iterable<LinuxProcess> {
            private final Map<Integer/*pid*/,LinuxProcess> processes = new HashMap<Integer,LinuxProcess>();

            LinuxSystem() {
                File[] processes = new File("/proc").listFiles(new FileFilter() {
                    public boolean accept(File f) {
                        return f.isDirectory();
                    }
                });
                if(processes==null) {
                    LOGGER.info("No /proc");
                    return;
                }

                for (File p : processes) {
                    int pid;
                    try {
                        pid = Integer.parseInt(p.getName());
                    } catch (NumberFormatException e) {
                        // other sub-directories
                        continue;
                    }
                    try {
                        this.processes.put(pid,new LinuxProcess(this,pid));
                    } catch (IOException e) {
                        // perhaps the process status has changed since we obtained a directory listing
                    }
                }
            }

            public LinuxProcess get(int pid) {
                return processes.get(pid);
            }

            public Iterator<LinuxProcess> iterator() {
                return processes.values().iterator();
            }
        }

        /**
         * A process.
         */
        static class LinuxProcess {
            private final LinuxSystem system;
            final int pid;
            private int ppid = -1;
            private EnvVars envVars;

            LinuxProcess(LinuxSystem system, int pid) throws IOException {
                this.system = system;
                this.pid = pid;

                BufferedReader r = new BufferedReader(new FileReader(getFile("status")));
                String line;
                while((line=r.readLine())!=null) {
                    line=line.toLowerCase(Locale.ENGLISH);
                    if(line.startsWith("ppid:"))
                        ppid = Integer.parseInt(line.substring(4).trim());
                }
                if(ppid==-1)
                    throw new IOException("Failed to parse PPID from /proc/"+pid+"/status");
            }

            private File getFile(String relativePath) {
                return new File(new File("/proc/"+pid),relativePath);
            }

            /**
             * Gets the parent process. This method may return null, because
             * there's no guarantee that we are getting a consistent snapshot
             * of the whole system state.
             */
            LinuxProcess getParent() {
                return system.get(ppid);
            }

            /**
             * Immediate child processes.
             */
            List<LinuxProcess> getChildren() {
                List<LinuxProcess> r = new ArrayList<LinuxProcess>();
                for (LinuxProcess p : system.processes.values())
                    if(p.ppid==pid)
                        r.add(p);
                return r;
            }

            public void killRecursively() {
                for (LinuxProcess p : getChildren())
                    p.killRecursively();
                kill();
            }

            /**
             * Tries to kill this process.
             */
            public void kill() {
                try {
                    DESTROY_PROCESS.invoke(null,pid);
                } catch (IllegalAccessException e) {
                    // this is impossible
                    IllegalAccessError x = new IllegalAccessError();
                    x.initCause(e);
                    throw x;
                } catch (InvocationTargetException e) {
                    // tunnel serious errors
                    if(e.getTargetException() instanceof Error)
                        throw (Error)e.getTargetException();
                    // otherwise log and let go. I need to see when this happens
                    LOGGER.log(Level.INFO, "Failed to terminate pid="+pid,e);
                }

            }

            /**
             * Obtains the environment variables of this process.
             *
             * @return
             *      empty map if failed (for example because the process is already dead,
             *      or the permission was denied.)
             */
            public synchronized EnvVars getEnvVars() {
                if(envVars !=null)
                    return envVars;
                envVars = new EnvVars();
                try {
                    byte[] environ = FileUtils.readFileToByteArray(getFile("environ"));
                    int pos=0;
                    for (int i = 0; i < environ.length; i++) {
                        byte b = environ[i];
                        if(b==0) {
                            String line = new String(environ,pos,i-pos);
                            int sep = line.indexOf('=');
                            envVars.put(line.substring(0,sep),line.substring(sep+1));
                            pos=i+1;
                        }
                    }
                } catch (IOException e) {
                    // failed to read. this can happen under normal circumstances,
                    // so don't report this as an error.
                }
                return envVars;
            }
        }

        public void kill(Process proc) {
            kill(proc,null);
        }

        public void kill(Process proc, Run<?,?> run) {
            LinuxSystem system = new LinuxSystem();
            LinuxProcess p;
            try {
                p = system.get((Integer) PID_FIELD.get(proc));
            } catch (IllegalAccessException e) { // impossible
                IllegalAccessError x = new IllegalAccessError();
                x.initCause(e);
                throw x;
            }

            if(p==null) {
                // process already dead?
                proc.destroy();
                return;
            }

            if(run==null)
                p.killRecursively();
            else {
                Map<String,String> modelEnvVars = run.getEnvVars();
                for (LinuxProcess lp : system) {
                    if(hasMatchingEnvVars(lp.getEnvVars(),modelEnvVars))
                        lp.kill();
                }
            }
        }

        /**
         * Field to access the PID of the process.
         */
        private static final Field PID_FIELD;

        /**
         * Method to destroy a process, given pid.
         */
        private static final Method DESTROY_PROCESS;

        static {
            try {
                Class<?> clazz = Class.forName("java.lang.UNIXProcess");
                PID_FIELD = clazz.getDeclaredField("pid");
                PID_FIELD.setAccessible(true);

                DESTROY_PROCESS = clazz.getDeclaredMethod("destroyProcess",int.class);
                DESTROY_PROCESS.setAccessible(true);
            } catch (ClassNotFoundException e) {
                LinkageError x = new LinkageError();
                x.initCause(e);
                throw x;
            } catch (NoSuchFieldException e) {
                LinkageError x = new LinkageError();
                x.initCause(e);
                throw x;
            } catch (NoSuchMethodException e) {
                LinkageError x = new LinkageError();
                x.initCause(e);
                throw x;
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ProcessTreeKiller.class.getName());
}
