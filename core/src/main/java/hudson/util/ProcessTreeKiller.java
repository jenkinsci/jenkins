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
package hudson.util;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import static com.sun.jna.Pointer.NULL;
import com.sun.jna.ptr.IntByReference;
import hudson.EnvVars;
import hudson.Util;
import static hudson.util.jna.GNUCLibrary.LIBC;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;
import java.util.logging.Logger;

/**
 * Kills a process tree to clean up the mess left by a build.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.201
 */
public abstract class ProcessTreeKiller {
    /**
     * Short for {@code kill(proc,null)}
     */
    public void kill(Process proc) {
        kill(proc,null);
    }

    /**
     * In addition to what {@link #kill(Process)} does, also tries to
     * kill all the daemon processes launched.
     *
     * <p>
     * Kills the given process (like {@link Process#destroy()}
     * but also attempts to kill descendant processes created from the given
     * process.
     *
     * <p>
     * In addition, optionally perform "search and destroy" based on environment
     * variables. In this method, the method is given a
     * "model environment variables", which is a list of environment variables
     * and their values that are characteristic to the launched process.
     * The implementation is expected to find processes
     * in the system that inherit these environment variables, and kill
     * them all. This is suitable for locating daemon processes
     * that cannot be tracked by the regular
     *
     * <p>
     * The implementation is obviously OS-dependent.
     *
     * @param proc
     *      Process to be killed recursively. Can be null.
     * @param modelEnvVars
     *      If non-null, search-and-destroy will be performed.
     */
    public abstract void kill(Process proc, Map<String, String> modelEnvVars);

    /**
     * Short for {@code kill(null,modelEnvVars)}
     */
    public void kill(Map<String, String> modelEnvVars) {
        kill(null,modelEnvVars);
    }

    /**
     * Creates a magic cookie that can be used as the model environment variable
     * when we later kill the processes.
     */
    public static EnvVars createCookie() {
        return new EnvVars("HUDSON_COOKIE",UUID.randomUUID().toString());
    }

    /**
     * Gets the {@link ProcessTreeKiller} suitable for the current system
     * that JVM runs in, or in the worst case return the default one
     * that's not capable of killing descendants at all.
     */
    public static ProcessTreeKiller get() {
        if(!enabled)
            return DEFAULT;

        try {
            if(File.pathSeparatorChar==';')
                return new Windows();

            String os = Util.fixNull(System.getProperty("os.name"));
            if(os.equals("Linux"))
                return new Linux();
            if(os.equals("SunOS"))
                return new Solaris();
            if(os.equals("Mac OS X"))
                return new Darwin();
        } catch (LinkageError e) {
            LOGGER.log(Level.WARNING,"Failed to load winp. Reverting to the default",e);
            enabled = false;
        }

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
        public void kill(Process proc, Map<String, String> modelEnvVars) {
            if (proc!=null)
                proc.destroy();
            // kill by model unsupported
        }
    };

    /**
     * Implementation for Windows.
     *
     * <p>
     * Not a singleton pattern because loading this class requires Windows specific library.
     */
    private static final class Windows extends ProcessTreeKiller {
        public void kill(Process proc, Map<String,String> modelEnvVars) {
            if(proc!=null)
                new WinProcess(proc).killRecursively();

            if(modelEnvVars!=null) {
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
        }

        static {
            WinProcess.enableDebugPrivilege();
        }
    }

    /**
     * Implementation for Unix that supports reasonably powerful <tt>/proc</tt> FS.
     */
    private static abstract class Unix<S extends Unix.UnixSystem<?>> extends ProcessTreeKiller {
        protected abstract S createSystem();

        public void kill(Process proc, Map<String, String> modelEnvVars) {
            S system = createSystem();

            if(proc!=null) {
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
                    // call destroy if only for a kick
                    proc.destroy();
                } else {
                    p.killRecursively();
                    proc.destroy(); // just for a kick
                }
            }

            if(modelEnvVars!=null) {
                for (UnixProcess lp : system) {
                    if(hasMatchingEnvVars(lp.getEnvVars(),modelEnvVars))
                        lp.killRecursively();
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

        /**
         * Represents a single Unix system, which hosts multiple processes.
         *
         * <p>
         * The object represents a snapshot of the system state.
         */
        static abstract class UnixSystem<P extends UnixProcess<P>> implements Iterable<P> {
            /**
             * To be filled in the constructor of the derived type.
             */
            protected final Map<Integer/*pid*/,P> processes = new HashMap<Integer,P>();

            public P get(int pid) {
                return processes.get(pid);
            }

            public Iterator<P> iterator() {
                return processes.values().iterator();
            }
        }

        /**
         * {@link UnixSystem} that has /proc.
         */
        static abstract class ProcfsUnixSystem<P extends UnixProcess<P>> extends UnixSystem<P> {
            ProcfsUnixSystem() {
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

        static class LinuxSystem extends Unix.ProcfsUnixSystem<LinuxProcess> {
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

        static class SolarisSystem extends Unix.ProcfsUnixSystem<SolarisProcess> {
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

                    // see http://cvs.opensolaris.org/source/xref/onnv/onnv-gate/usr/src/cmd/ptools/pargs/pargs.c
                    // for how to read this information
                    
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
                            int p = adjust(as.readInt());

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
                            int p = adjust(as.readInt());
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

    /**
     * Implementation for Mac OS X based on sysctl(3).
     */
    private static final class Darwin extends Unix<Darwin.DarwinSystem> {
        protected DarwinSystem createSystem() {
            return new DarwinSystem();
        }

        static class DarwinSystem extends Unix.UnixSystem<DarwinProcess> {
            DarwinSystem() {
                try {
                    IntByReference _ = new IntByReference(sizeOfInt);
                    IntByReference size = new IntByReference(sizeOfInt);
                    Memory m;
                    int nRetry = 0;
                    while(true) {
                        // find out how much memory we need to do this
                        if(LIBC.sysctl(MIB_PROC_ALL,3, NULL, size, NULL, _)!=0)
                            throw new IOException("Failed to obtain memory requirement: "+LIBC.strerror(Native.getLastError()));

                        // now try the real call
                        m = new Memory(size.getValue());
                        if(LIBC.sysctl(MIB_PROC_ALL,3, m, size, NULL, _)!=0) {
                            if(Native.getLastError()==ENOMEM && nRetry++<16)
                                continue; // retry
                            throw new IOException("Failed to call kern.proc.all: "+LIBC.strerror(Native.getLastError()));
                        }
                        break;
                    }

                    int count = size.getValue()/sizeOf_kinfo_proc;
                    LOGGER.fine("Found "+count+" processes");

                    for( int base=0; base<size.getValue(); base+=sizeOf_kinfo_proc) {
                        int pid = m.getInt(base+24);
                        int ppid = m.getInt(base+416);
    //                    int effective_uid = m.getInt(base+304);
    //                    byte[] comm = new byte[16];
    //                    m.read(base+163,comm,0,16);

                        processes.put(pid,new DarwinProcess(this,pid,ppid));
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to obtain process list",e);
                }
            }
        }

        static class DarwinProcess extends Unix.UnixProcess<DarwinProcess> {
            private final int pid;
            private final int ppid;
            private EnvVars envVars;
            private List<String> arguments;

            DarwinProcess(DarwinSystem system, int pid, int ppid) {
                super(system);
                this.pid = pid;
                this.ppid = ppid;
            }

            public int getPid() {
                return pid;
            }

            public DarwinProcess getParent() {
                return system.get(ppid);
            }

            public synchronized EnvVars getEnvVars() {
                if(envVars !=null)
                    return envVars;
                parse();
                return envVars;
            }

            public List<String> getArguments() {
                if(arguments !=null)
                    return arguments;
                parse();
                return arguments;
            }

            private void parse() {
                try {
// allocate them first, so that the parse error wil result in empty data
                    // and avoid retry.
                    arguments = new ArrayList<String>();
                    envVars = new EnvVars();

                    IntByReference _ = new IntByReference();

                    IntByReference argmaxRef = new IntByReference(0);
                    IntByReference size = new IntByReference(sizeOfInt);

                    // for some reason, I was never able to get sysctlbyname work.
//        if(LIBC.sysctlbyname("kern.argmax", argmaxRef.getPointer(), size, NULL, _)!=0)
                    if(LIBC.sysctl(new int[]{CTL_KERN,KERN_ARGMAX},2, argmaxRef.getPointer(), size, NULL, _)!=0)
                        throw new IOException("Failed to get kernl.argmax: "+LIBC.strerror(Native.getLastError()));

                    int argmax = argmaxRef.getValue();

                    class StringArrayMemory extends Memory {
                        private long offset=0;

                        StringArrayMemory(long l) {
                            super(l);
                        }

                        int readInt() {
                            int r = getInt(offset);
                            offset+=sizeOfInt;
                            return r;
                        }

                        byte peek() {
                            return getByte(offset);
                        }

                        String readString() {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte ch;
                            while((ch = getByte(offset++))!='\0')
                                baos.write(ch);
                            return baos.toString();
                        }

                        void skip0() {
                            // skip trailing '\0's
                            while(getByte(offset)=='\0')
                                offset++;
                        }
                    }
                    StringArrayMemory m = new StringArrayMemory(argmax);
                    size.setValue(argmax);
                    if(LIBC.sysctl(new int[]{CTL_KERN,KERN_PROCARGS2,pid},3, m, size, NULL, _)!=0)
                        throw new IOException("Failed to obtain ken.procargs2: "+LIBC.strerror(Native.getLastError()));


                    /*
                    * Make a sysctl() call to get the raw argument space of the
                        * process.  The layout is documented in start.s, which is part
                        * of the Csu project.  In summary, it looks like:
                        *
                        * /---------------\ 0x00000000
                        * :               :
                        * :               :
                        * |---------------|
                        * | argc          |
                        * |---------------|
                        * | arg[0]        |
                        * |---------------|
                        * :               :
                        * :               :
                        * |---------------|
                        * | arg[argc - 1] |
                        * |---------------|
                        * | 0             |
                        * |---------------|
                        * | env[0]        |
                        * |---------------|
                        * :               :
                        * :               :
                        * |---------------|
                        * | env[n]        |
                        * |---------------|
                        * | 0             |
                        * |---------------| <-- Beginning of data returned by sysctl()
                        * | exec_path     |     is here.
                        * |:::::::::::::::|
                        * |               |
                        * | String area.  |
                        * |               |
                        * |---------------| <-- Top of stack.
                        * :               :
                        * :               :
                        * \---------------/ 0xffffffff
                        */

                    int nargs = m.readInt();
                    m.readString(); // exec path
                    for( int i=0; i<nargs; i++) {
                        m.skip0();
                        arguments.add(m.readString());
                    }

                    // this is how you can read environment variables
                    while(m.peek()!=0)
                    envVars.addLine(m.readString());
                } catch (IOException e) {
                    // this happens with insufficient permissions, so just ignore the problem.
                }
            }
        }
        
        // local constants
        private static final int sizeOf_kinfo_proc = 492; // TODO:checked on 32bit Mac OS X. is this different on 64bit?
        private static final int CTL_KERN = 1;
        private static final int KERN_PROC = 14;
        private static final int KERN_PROC_ALL = 0;
        private static final int KERN_ARGMAX = 8;
        private static final int KERN_PROCARGS2 = 49;
        private static final int ENOMEM = 12;
        private static final int sizeOfInt = Native.getNativeSize(int.class);
        private static int[] MIB_PROC_ALL = {CTL_KERN, KERN_PROC, KERN_PROC_ALL};
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
