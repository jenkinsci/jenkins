package hudson.util;

import hudson.EnvVars;
import hudson.Util;
import org.apache.commons.io.FileUtils;
import org.jvnet.winp.WinProcess;
import org.jvnet.winp.WinpException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.FINER;

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
     * Daemon processes are hard to find because they normally detach themselves
     * from the parent process. In this method, the method is given a
     * "model environment variables", which is a list of environment variables
     * and their values that are characteristic to the launched process.
     * The implementation is expected to find processes
     * in the system that inherit these environment variables, and kill
     * them even if the {@code proc} and such processes do not have direct
     * ancestor/descendant relationship. 
     */
    public abstract void kill(Process proc, Map<String, String> modelEnvVars);

    /**
     * Creates a magic cookie that can be used as the model environment variable
     * when we later kill the processes.
     */
    public static EnvVars createCookie() {
        EnvVars r = new EnvVars();
        r.put("HUDSON_COOKIE",UUID.randomUUID().toString());
        return r;
    }

    /**
     * Gets the {@link ProcessTreeKiller} suitable for the current system
     * that JVM runs in, or in the worst case return the default one
     * that's not capable of killing descendants at all.
     */
    public static ProcessTreeKiller get() {
        if(!enabled)
            return DEFAULT;

        if(File.pathSeparatorChar==';')
            return new Windows();

        String os = Util.fixNull(System.getProperty("os.name"));
        if(os.equals("Linux"))
            return new Linux();
        if(os.equals("SunOS"))
            return new Solaris();

        return DEFAULT;
    }


    /**
     * Given the environment variable of a process and the "model environment variable" that Hudson
     * used for launching the build, returns true if there's a match (which means the process should
     * be considered a descendant of a build.) 
     */
    protected boolean hasMatchingEnvVars(Map<String,String> envVar, Map<String,String> modelEnvVar) {
        if(modelEnvVar.isEmpty())
            // sanity check so that we don't start rampage.
            return false;

        for (Entry<String,String> e : modelEnvVar.entrySet()) {
            String v = envVar.get(e.getKey());
            if(v==null || !v.equals(e.getValue()))
                return false;   // no match
        }

        return true;
    }


    /**
     * Fallback implementation that doesn't do anything clever.
     */
    private static final ProcessTreeKiller DEFAULT = new ProcessTreeKiller() {
        public void kill(Process proc) {
            proc.destroy();
        }

        public void kill(Process proc, Map<String, String> modelEnvVars) {
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

        public void kill(Process proc, Map<String,String> modelEnvVars) {
            kill(proc);

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
     * Implementation for Unix that supports reasonably powerful <tt>/proc</tt> FS.
     */
    private static abstract class Unix<S extends Unix.UnixSystem<?>> extends ProcessTreeKiller {
        public void kill(Process proc) {
            kill(proc,null);
        }

        protected abstract S createSystem();

        public void kill(Process proc, Map<String, String> modelEnvVars) {
            S system = createSystem();
            UnixProcess p;
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

            if(modelEnvVars ==null)
                p.killRecursively();
            else {
                for (UnixProcess lp : system) {
                    if(hasMatchingEnvVars(lp.getEnvVars(),modelEnvVars))
                        lp.kill();
                }
            }

            // in case the child enumeration fails for some reasons
            // (like the child process changing environment variables by itself),
            // let's make sure that at least 'proc' is destroyed.
            proc.destroy();
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

        /**
         * Represents a single Unix system, which hosts multiple processes.
         *
         * <p>
         * The object represents a snapshot of the system state.
         */
        static abstract class UnixSystem<P extends UnixProcess<P>> implements Iterable<P> {
            private final Map<Integer/*pid*/,P> processes = new HashMap<Integer,P>();

            UnixSystem() {
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
                        this.processes.put(pid,createProcess(pid));
                    } catch (IOException e) {
                        // perhaps the process status has changed since we obtained a directory listing
                    }
                }
            }

            protected abstract P createProcess(int pid) throws IOException;

            public P get(int pid) {
                return processes.get(pid);
            }

            public Iterator<P> iterator() {
                return processes.values().iterator();
            }
        }

        /**
         * A process.
         */
        public static abstract class UnixProcess<P extends UnixProcess<P>> {
            public final UnixSystem<P>  system;

            protected UnixProcess(UnixSystem<P> system) {
                this.system = system;
            }

            public abstract int getPid();

            /**
             * Gets the parent process. This method may return null, because
             * there's no guarantee that we are getting a consistent snapshot
             * of the whole system state.
             */
            public abstract P getParent();

            protected final File getFile(String relativePath) {
                return new File(new File("/proc/"+getPid()),relativePath);
            }

            /**
             * Immediate child processes.
             */
            public List<P> getChildren() {
                List<P> r = new ArrayList<P>();
                for (P p : system)
                    if(p.getParent()==this)
                        r.add(p);
                return r;
            }

            /**
             * Tries to kill this process.
             */
            public void kill() {
                try {
                    DESTROY_PROCESS.invoke(null,getPid());
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
                    LOGGER.log(Level.INFO, "Failed to terminate pid="+getPid(),e);
                }

            }

            public void killRecursively() {
                for (P p : getChildren())
                    p.killRecursively();
                kill();
            }

            /**
             * Obtains the environment variables of this process.
             *
             * @return
             *      empty map if failed (for example because the process is already dead,
             *      or the permission was denied.)
             */
            public abstract EnvVars getEnvVars();

            /**
             * Obtains the argument list of this process.
             *
             * @return
             *      empty list if failed (for example because the process is already dead,
             *      or the permission was denied.)
             */
            public abstract List<String> getArguments();
        }
    }



    /**
     * Implementation for Linux that uses <tt>/proc</tt>.
     */
    private static final class Linux extends Unix<Linux.LinuxSystem> {
        protected LinuxSystem createSystem() {
            return new LinuxSystem();
        }

        static class LinuxSystem extends Unix.UnixSystem<LinuxProcess> {
            protected LinuxProcess createProcess(int pid) throws IOException {
                return new LinuxProcess(this,pid);
            }
        }

        static class LinuxProcess extends Unix.UnixProcess<LinuxProcess> {
            private final int pid;
            private int ppid = -1;
            private EnvVars envVars;
            private List<String> arguments;

            LinuxProcess(LinuxSystem system, int pid) throws IOException {
                super(system);
                this.pid = pid;

                BufferedReader r = new BufferedReader(new FileReader(getFile("status")));
                try {
                    String line;
                    while((line=r.readLine())!=null) {
                        line=line.toLowerCase(Locale.ENGLISH);
                        if(line.startsWith("ppid:")) {
                            ppid = Integer.parseInt(line.substring(5).trim());
                            break;
                        }
                    }
                } finally {
                    r.close();
                }
                if(ppid==-1)
                    throw new IOException("Failed to parse PPID from /proc/"+pid+"/status");
            }

            public int getPid() {
                return pid;
            }

            public LinuxProcess getParent() {
                return system.get(ppid);
            }

            public synchronized List<String> getArguments() {
                if(arguments!=null)
                    return arguments;
                arguments = new ArrayList<String>();
                try {
                    byte[] cmdline = FileUtils.readFileToByteArray(getFile("cmdline"));
                    int pos=0;
                    for (int i = 0; i < cmdline.length; i++) {
                        byte b = cmdline[i];
                        if(b==0) {
                            arguments.add(new String(cmdline,pos,i-pos));
                            pos=i+1;
                        }
                    }
                } catch (IOException e) {
                    // failed to read. this can happen under normal circumstances (most notably permission denied)
                    // so don't report this as an error.
                }
                arguments = Collections.unmodifiableList(arguments);
                return arguments;                
            }

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
                            envVars.addLine(new String(environ,pos,i-pos));
                            pos=i+1;
                        }
                    }
                } catch (IOException e) {
                    // failed to read. this can happen under normal circumstances (most notably permission denied)
                    // so don't report this as an error.
                }
                return envVars;
            }
        }
    }



    /**
     * Implementation for Solaris that uses <tt>/proc</tt>.
     *
     * Amazingly, this single code works for both 32bit and 64bit Solaris, despite the fact
     * that does a lot of pointer manipulation and what not.
     */
    private static final class Solaris extends Unix<Solaris.SolarisSystem> {
        protected SolarisSystem createSystem() {
            return new SolarisSystem();
        }

        static class SolarisSystem extends Unix.UnixSystem<SolarisProcess> {
            protected SolarisProcess createProcess(int pid) throws IOException {
                return new SolarisProcess(this,pid);
            }
        }

        static class SolarisProcess extends Unix.UnixProcess<SolarisProcess> {
            private final int pid;
            private final int ppid;
            /**
             * Address of the environment vector. Even on 64bit Solaris this is still 32bit pointer.
             */
            private final int envp;
            /**
             * Similarly, address of the arguments vector.
             */
            private final int argp;
            private final int argc;
            private EnvVars envVars;
            private List<String> arguments;

            SolarisProcess(SolarisSystem system, int pid) throws IOException {
                super(system);
                this.pid = pid;

                RandomAccessFile psinfo = new RandomAccessFile(getFile("psinfo"),"r");
                try {
                    // see http://cvs.opensolaris.org/source/xref/onnv/onnv-gate/usr/src/uts/common/sys/procfs.h
                    //typedef struct psinfo {
                    //	int	pr_flag;	/* process flags */
                    //	int	pr_nlwp;	/* number of lwps in the process */
                    //	pid_t	pr_pid;	/* process id */
                    //	pid_t	pr_ppid;	/* process id of parent */
                    //	pid_t	pr_pgid;	/* process id of process group leader */
                    //	pid_t	pr_sid;	/* session id */
                    //	uid_t	pr_uid;	/* real user id */
                    //	uid_t	pr_euid;	/* effective user id */
                    //	gid_t	pr_gid;	/* real group id */
                    //	gid_t	pr_egid;	/* effective group id */
                    //	uintptr_t	pr_addr;	/* address of process */
                    //	size_t	pr_size;	/* size of process image in Kbytes */
                    //	size_t	pr_rssize;	/* resident set size in Kbytes */
                    //	dev_t	pr_ttydev;	/* controlling tty device (or PRNODEV) */
                    //	ushort_t	pr_pctcpu;	/* % of recent cpu time used by all lwps */
                    //	ushort_t	pr_pctmem;	/* % of system memory used by process */
                    //	timestruc_t	pr_start;	/* process start time, from the epoch */
                    //	timestruc_t	pr_time;	/* cpu time for this process */
                    //	timestruc_t	pr_ctime;	/* cpu time for reaped children */
                    //	char	pr_fname[PRFNSZ];	/* name of exec'ed file */
                    //	char	pr_psargs[PRARGSZ];	/* initial characters of arg list */
                    //	int	pr_wstat;	/* if zombie, the wait() status */
                    //	int	pr_argc;	/* initial argument count */
                    //	uintptr_t	pr_argv;	/* address of initial argument vector */
                    //	uintptr_t	pr_envp;	/* address of initial environment vector */
                    //	char	pr_dmodel;	/* data model of the process */
                    //	lwpsinfo_t	pr_lwp;	/* information for representative lwp */
                    //} psinfo_t;

                    // see http://cvs.opensolaris.org/source/xref/onnv/onnv-gate/usr/src/uts/common/sys/types.h
                    // for the size of the various datatype.

                    psinfo.seek(8);
                    if(adjust(psinfo.readInt())!=pid)
                        throw new IOException("psinfo PID mismatch");   // sanity check
                    ppid = adjust(psinfo.readInt());

                    psinfo.seek(188);  // now jump to pr_argc
                    argc = adjust(psinfo.readInt());
                    argp = adjust(psinfo.readInt());
                    envp = adjust(psinfo.readInt());
                } finally {
                    psinfo.close();
                }
                if(ppid==-1)
                    throw new IOException("Failed to parse PPID from /proc/"+pid+"/status");
            }

            /**
             * {@link DataInputStream} reads a value in big-endian, so
             * convert it to the correct value on little-endian systems.
             */
            private int adjust(int i) {
                if(IS_LITTLE_ENDIAN)
                    return (i<<24) |((i<<8) & 0x00FF0000) | ((i>>8) & 0x0000FF00) | (i>>>24);
                else
                    return i;
            }

            public int getPid() {
                return pid;
            }

            public SolarisProcess getParent() {
                return system.get(ppid);
            }

            public synchronized List<String> getArguments() {
                if(arguments!=null)
                    return arguments;

                arguments = new ArrayList<String>(argc);

                try {
                    RandomAccessFile as = new RandomAccessFile(getFile("as"),"r");
                    if(LOGGER.isLoggable(FINER))
                        LOGGER.finer("Reading "+getFile("as"));
                    try {
                        for( int n=0; n<argc; n++ ) {
                            // read a pointer to one entry
                            as.seek(to64(argp+n*4));
                            int p = as.readInt();

                            arguments.add(readLine(as, p, "argv["+ n +"]"));
                        }
                    } finally {
                        as.close();
                    }
                } catch (IOException e) {
                    // failed to read. this can happen under normal circumstances (most notably permission denied)
                    // so don't report this as an error.
                }

                arguments = Collections.unmodifiableList(arguments);
                return arguments;
            }

            public synchronized EnvVars getEnvVars() {
                if(envVars !=null)
                    return envVars;
                envVars = new EnvVars();

                try {
                    RandomAccessFile as = new RandomAccessFile(getFile("as"),"r");
                    if(LOGGER.isLoggable(FINER))
                        LOGGER.finer("Reading "+getFile("as"));
                    try {
                        for( int n=0; ; n++ ) {
                            // read a pointer to one entry
                            as.seek(to64(envp+n*4));
                            int p = as.readInt();
                            if(p==0)
                                break;  // completed the walk

                            // now read the null-terminated string
                            envVars.addLine(readLine(as, p, "env["+ n +"]"));
                        }
                    } finally {
                        as.close();
                    }
                } catch (IOException e) {
                    // failed to read. this can happen under normal circumstances (most notably permission denied)
                    // so don't report this as an error.
                }
                
                return envVars;
            }

            private String readLine(RandomAccessFile as, int p, String prefix) throws IOException {
                if(LOGGER.isLoggable(FINEST))
                    LOGGER.finest("Reading "+prefix+" at "+p);

                as.seek(to64(p));
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                int ch,i=0;
                while((ch=as.read())>0) {
                    if((++i)%100==0 && LOGGER.isLoggable(FINEST))
                        LOGGER.finest(prefix +" is so far "+buf.toString());

                    buf.write(ch);
                }
                String line = buf.toString();
                if(LOGGER.isLoggable(FINEST))
                    LOGGER.finest(prefix+" was "+line);
                return line;
            }

            /**
             * int to long conversion with zero-padding.
             */
            private static long to64(int i) {
                return i&0xFFFFFFFFL;
            }
        }
    }

//    public static void main(String[] args) {
//        // dump everything
//        LOGGER.setLevel(Level.ALL);
//        ConsoleHandler h = new ConsoleHandler();
//        h.setLevel(Level.ALL);
//        LOGGER.addHandler(h);
//
//        Solaris killer = (Solaris)get();
//        Solaris.SolarisSystem s = killer.createSystem();
//        Solaris.SolarisProcess p = s.get(Integer.parseInt(args[0]));
//        System.out.println(p.getEnvVars());
//
//        if(args.length==2)
//            p.kill();
//    }

    /*
        On MacOS X, there's no procfs <http://www.osxbook.com/book/bonus/chapter11/procfs/>
        instead you'd do it with the sysctl <http://search.cpan.org/src/DURIST/Proc-ProcessTable-0.42/os/darwin.c>
        <http://developer.apple.com/documentation/Darwin/Reference/ManPages/man3/sysctl.3.html>

        There's CLI but that doesn't seem to offer the access to per-process info
        <http://developer.apple.com/documentation/Darwin/Reference/ManPages/man8/sysctl.8.html>



        On HP-UX, pstat_getcommandline get you command line, but I'm not seeing any environment variables.
     */

    private static final boolean IS_LITTLE_ENDIAN = "little".equals(System.getProperty("sun.cpu.endian"));
    private static final Logger LOGGER = Logger.getLogger(ProcessTreeKiller.class.getName());

    /**
     * Flag to control this feature.
     *
     * <p>
     * This feature involves some native code, so we are allowing the user to disable this
     * in case there's a fatal problem.
     */
    public static boolean enabled = !Boolean.getBoolean(ProcessTreeKiller.class.getName()+".disable");
}
