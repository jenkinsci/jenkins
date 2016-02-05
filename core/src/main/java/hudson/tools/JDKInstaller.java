/*
 * The MIT License
 *
 * Copyright (c) 2009-2010, Sun Microsystems, Inc., CloudBees, Inc.
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
package hudson.tools;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.model.DownloadService.Downloadable;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import net.sf.json.JSONObject;
import net.sf.json.JsonConfig;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;

import javax.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static hudson.tools.JDKInstaller.Preference.*;

/**
 * Install JDKs from java.sun.com.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.305
 */
public class JDKInstaller extends ToolInstaller {

    static {
        // this socket factory will not attempt to bind to the client interface
        Protocol.registerProtocol("http", new Protocol("http", new hudson.util.NoClientBindProtocolSocketFactory(), 80));
        Protocol.registerProtocol("https", new Protocol("https", new hudson.util.NoClientBindSSLProtocolSocketFactory(), 443));
    }

    /**
     * The release ID that Sun assigns to each JDK, such as "jdk-6u13-oth-JPR@CDS-CDS_Developer"
     *
     * <p>
     * This ID can be seen in the "ProductRef" query parameter of the download page, like
     * https://cds.sun.com/is-bin/INTERSHOP.enfinity/WFS/CDS-CDS_Developer-Site/en_US/-/USD/ViewProductDetail-Start?ProductRef=jdk-6u13-oth-JPR@CDS-CDS_Developer
     */
    public final String id;

    /**
     * We require that the user accepts the license by clicking a checkbox, to make up for the part
     * that we auto-accept cds.sun.com license click through.
     */
    public final boolean acceptLicense;

    @DataBoundConstructor
    public JDKInstaller(String id, boolean acceptLicense) {
        super(null);
        this.id = id;
        this.acceptLicense = acceptLicense;
    }

    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws IOException, InterruptedException {
        FilePath expectedLocation = preferredLocation(tool, node);
        PrintStream out = log.getLogger();
        try {
            if(!acceptLicense) {
                out.println(Messages.JDKInstaller_UnableToInstallUntilLicenseAccepted());
                return expectedLocation;
            }
            // already installed?
            FilePath marker = expectedLocation.child(".installedByHudson");
            if (marker.exists() && marker.readToString().equals(id)) {
                return expectedLocation;
            }
            expectedLocation.deleteRecursive();
            expectedLocation.mkdirs();

            Platform p = Platform.of(node);
            URL url = locate(log, p, CPU.of(node));

//            out.println("Downloading "+url);
            FilePath file = expectedLocation.child(p.bundleFileName);
            file.copyFrom(url);

            // JDK6u13 on Windows doesn't like path representation like "/tmp/foo", so make it a strict platform native format by doing 'absolutize'
            install(node.createLauncher(log), p, new FilePathFileSystem(node), log, expectedLocation.absolutize().getRemote(), file.getRemote());

            // successfully installed
            file.delete();
            marker.write(id, null);

        } catch (DetectionFailedException e) {
            out.println("JDK installation skipped: "+e.getMessage());
        }

        return expectedLocation;
    }

    /**
     * Performs the JDK installation to a system, provided that the bundle was already downloaded.
     *
     * @param launcher
     *      Used to launch processes on the system.
     * @param p
     *      Platform of the system. This determines how the bundle is installed.
     * @param fs
     *      Abstraction of the file system manipulation on this system.
     * @param log
     *      Where the output from the installation will be written.
     * @param expectedLocation
     *      Path to install JDK to. Must be absolute and in the native file system notation.
     * @param jdkBundle
     *      Path to the installed JDK bundle. (The bundle to download can be determined by {@link #locate(TaskListener, Platform, CPU)} call.)
     */
    public void install(Launcher launcher, Platform p, FileSystem fs, TaskListener log, String expectedLocation, String jdkBundle) throws IOException, InterruptedException {
        PrintStream out = log.getLogger();

        out.println("Installing "+ jdkBundle);
        FilePath parent = new FilePath(launcher.getChannel(), expectedLocation).getParent();
        switch (p) {
        case LINUX:
        case SOLARIS:
            // JDK on Unix up to 6 was distributed as shell script installer, but in JDK7 it switched to a plain tgz.
            // so check if the file is gzipped, and if so, treat it accordingly
            byte[] header = new byte[2];
            {
                DataInputStream in = new DataInputStream(fs.read(jdkBundle));
                try {
                    in.readFully(header);
                } finally {
                    IOUtils.closeQuietly(in);
                }
            }

            ProcStarter starter;
            if (header[0]==0x1F && header[1]==(byte)0x8B) {// gzip
                starter = launcher.launch().cmds("tar", "xvzf", jdkBundle);
            } else {
                fs.chmod(jdkBundle,0755);
                starter = launcher.launch().cmds(jdkBundle, "-noregister");
            }

            int exit = starter
                    .stdin(new ByteArrayInputStream("yes".getBytes())).stdout(out)
                    .pwd(new FilePath(launcher.getChannel(), expectedLocation)).join();
            if (exit != 0)
                throw new AbortException(Messages.JDKInstaller_FailedToInstallJDK(exit));

            // JDK creates its own sub-directory, so pull them up
            List<String> paths = fs.listSubDirectories(expectedLocation);
            for (Iterator<String> itr = paths.iterator(); itr.hasNext();) {
                String s =  itr.next();
                if (!s.matches("j(2s)?dk.*"))
                    itr.remove();
            }
            if(paths.size()!=1)
                throw new AbortException("Failed to find the extracted JDKs: "+paths);

            // remove the intermediate directory
            fs.pullUp(expectedLocation+'/'+paths.get(0),expectedLocation);
            break;
        case WINDOWS:
            /*
                Windows silent installation is full of bad know-how.

                On Windows, command line argument to a process at the OS level is a single string,
                not a string array like POSIX. When we pass arguments as string array, JRE eventually
                turn it into a single string with adding quotes to "the right place". Unfortunately,
                with the strange argument layout of InstallShield (like /v/qn" INSTALLDIR=foobar"),
                it appears that the escaping done by JRE gets in the way, and prevents the installation.
                Presumably because of this, my attempt to use /q/vn" INSTALLDIR=foo" didn't work with JDK5.

                I tried to locate exactly how InstallShield parses the arguments (and why it uses
                awkward option like /qn, but couldn't find any. Instead, experiments revealed that
                "/q/vn ARG ARG ARG" works just as well. This is presumably due to the Visual C++ runtime library
                (which does single string -> string array conversion to invoke the main method in most Win32 process),
                and this consistently worked on JDK5 and JDK4.

                Some of the official documentations are available at
                - http://java.sun.com/j2se/1.5.0/sdksilent.html
                - http://java.sun.com/j2se/1.4.2/docs/guide/plugin/developer_guide/silent.html
             */

            expectedLocation = expectedLocation.trim();
            if (expectedLocation.endsWith("\\")) {
                // Prevent a trailing slash from escaping quotes
                expectedLocation = expectedLocation.substring(0, expectedLocation.length() - 1);
            }
            String logFile = parent.createTempFile("install", "log").getRemote();


            ArgumentListBuilder args = new ArgumentListBuilder();
            assert (new File(expectedLocation).exists()) : expectedLocation
                    + " must exist, otherwise /L will cause the installer to fail with error 1622";
            if (isJava15() || isJava14()) {
                // Installer uses InstallShield.
                args.add("CMD.EXE", "/C");

                // see http://docs.oracle.com/javase/1.5.0/docs/guide/deployment/deployment-guide/silent.html
                // CMD.EXE /C must be followed by a single parameter (do not split it!)
                args.add(jdkBundle + " /s /v\"/qn REBOOT=ReallySuppress INSTALLDIR=\\\""
                        + expectedLocation + "\\\" /L \\\"" + logFile + "\\\"\"");
            } else {
                // Installed uses Windows Installer (MSI)
                args.add(jdkBundle, "/s");

                // Create a private JRE by omitting "PublicjreFeature"
                // @see http://docs.oracle.com/javase/7/docs/webnotes/install/windows/jdk-installation-windows.html#jdk-silent-installation
                args.add("ADDLOCAL=\"ToolsFeature\"",
                        "REBOOT=ReallySuppress", "INSTALLDIR=" + expectedLocation,
                        "/L",  logFile);
            }
            int r = launcher.launch().cmds(args).stdout(out)
                    .pwd(new FilePath(launcher.getChannel(), expectedLocation)).join();
            if (r != 0) {
                out.println(Messages.JDKInstaller_FailedToInstallJDK(r));
                // log file is in UTF-16
                InputStreamReader in = new InputStreamReader(fs.read(logFile), "UTF-16");
                try {
                    IOUtils.copy(in,new OutputStreamWriter(out));
                } finally {
                    in.close();
                }
                throw new AbortException();
            }

            fs.delete(logFile);

            break;

        case OSX:
            // Mount the DMG distribution bundle
            FilePath dmg = parent.createTempDir("jdk", "dmg");
            exit = launcher.launch()
                    .cmds("hdiutil", "attach", "-puppetstrings", "-mountpoint", dmg.getRemote(), jdkBundle)
                    .stdout(log)
                    .join();
            if (exit != 0)
                throw new AbortException(Messages.JDKInstaller_FailedToInstallJDK(exit));

            // expand the installation PKG
            FilePath[] list = dmg.list("*.pkg");
            if (list.length != 1) {
                log.getLogger().println("JDK dmg bundle does not contain expected pkg installer");
                throw new AbortException(Messages.JDKInstaller_FailedToInstallJDK(exit));
            }
            String installer = list[0].getRemote();

            FilePath pkg = parent.createTempDir("jdk", "pkg");
            pkg.deleteRecursive(); // pkgutil fails if target directory exists
            exit = launcher.launch()
                    .cmds("pkgutil", "--expand", installer, pkg.getRemote())
                    .stdout(log)
                    .join();
            if (exit != 0)
                throw new AbortException(Messages.JDKInstaller_FailedToInstallJDK(exit));

            exit = launcher.launch()
                    .cmds("umount", dmg.getRemote())
                    .stdout(log)
                    .join();
            if (exit != 0)
                throw new AbortException(Messages.JDKInstaller_FailedToInstallJDK(exit));

            // We only want the actual JDK sub-package, which "Payload" is actually a tar.gz archive
            list = pkg.list("jdk*.pkg/Payload");
            if (list.length != 1) {
                log.getLogger().println("JDK pkg installer does not contain expected JDK Payload archive");
                throw new AbortException(Messages.JDKInstaller_FailedToInstallJDK(exit));
            }
            String payload = list[0].getRemote();
            exit = launcher.launch()
                    .pwd(parent).cmds("tar", "xzf", payload)
                    .stdout(log)
                    .join();
            if (exit != 0)
                throw new AbortException(Messages.JDKInstaller_FailedToInstallJDK(exit));

            parent.child("Contents/Home").moveAllChildrenTo(new FilePath(launcher.getChannel(), expectedLocation));
            parent.child("Contents").deleteRecursive();

            pkg.deleteRecursive();
            dmg.deleteRecursive();
            break;
        }
    }

    private boolean isJava15() {
        return id.contains("-1.5");
    }

    private boolean isJava14() {
        return id.contains("-1.4");
    }

    /**
     * Abstraction of the file system to perform JDK installation.
     * Consider {@link JDKInstaller.FilePathFileSystem} as the canonical documentation of the contract.
     */
    public interface FileSystem {
        void delete(String file) throws IOException, InterruptedException;
        void chmod(String file,int mode) throws IOException, InterruptedException;
        InputStream read(String file) throws IOException, InterruptedException;
        /**
         * List sub-directories of the given directory and just return the file name portion.
         */
        List<String> listSubDirectories(String dir) throws IOException, InterruptedException;
        void pullUp(String from, String to) throws IOException, InterruptedException;
    }

    /*package*/ static final class FilePathFileSystem implements FileSystem {
        private final Node node;

        FilePathFileSystem(Node node) {
            this.node = node;
        }

        public void delete(String file) throws IOException, InterruptedException {
            $(file).delete();
        }

        public void chmod(String file, int mode) throws IOException, InterruptedException {
            $(file).chmod(mode);
        }

        public InputStream read(String file) throws IOException, InterruptedException {
            return $(file).read();
        }

        public List<String> listSubDirectories(String dir) throws IOException, InterruptedException {
            List<String> r = new ArrayList<String>();
            for( FilePath f : $(dir).listDirectories())
                r.add(f.getName());
            return r;
        }

        public void pullUp(String from, String to) throws IOException, InterruptedException {
            $(from).moveAllChildrenTo($(to));
        }

        private FilePath $(String file) {
            return node.createPath(file);
        }
    }

    /**
     * This is where we locally cache this JDK.
     */
    private File getLocalCacheFile(Platform platform, CPU cpu) {
        return new File(Jenkins.getInstance().getRootDir(),"cache/jdks/"+platform+"/"+cpu+"/"+id);
    }

    /**
     * Performs a license click through and obtains the one-time URL for downloading bits.
     */
    public URL locate(TaskListener log, Platform platform, CPU cpu) throws IOException {
        File cache = getLocalCacheFile(platform, cpu);
        if (cache.exists() && cache.length()>1*1024*1024) return cache.toURL(); // if the file is too small, don't trust it. In the past, the download site served error message in 200 status code

        log.getLogger().println("Installing JDK "+id);
        JDKFamilyList families = JDKList.all().get(JDKList.class).toList();
        if (families.isEmpty())
            throw new IOException("JDK data is empty.");

        JDKRelease release = families.getRelease(id);
        if (release==null)
            throw new IOException("Unable to find JDK with ID="+id);

        JDKFile primary=null,secondary=null;
        for (JDKFile f : release.files) {
            String vcap = f.name.toUpperCase(Locale.ENGLISH);

            // JDK files have either 'windows', 'linux', or 'solaris' in its name, so that allows us to throw
            // away unapplicable stuff right away
            if(!platform.is(vcap))
                continue;

            switch (cpu.accept(vcap)) {
            case PRIMARY:   primary = f;break;
            case SECONDARY: secondary=f;break;
            case UNACCEPTABLE:  break;
            }
        }

        if(primary==null)   primary=secondary;
        if(primary==null)
            throw new AbortException("Couldn't find the right download for "+platform+" and "+ cpu +" combination");
        LOGGER.fine("Platform choice:"+primary);

        log.getLogger().println("Downloading JDK from "+primary.filepath);

        HttpClient hc = new HttpClient();
        hc.getParams().setParameter("http.useragent","Mozilla/5.0 (Windows; U; MSIE 9.0; Windows NT 9.0; en-US)");
        Jenkins j = Jenkins.getInstance();
        ProxyConfiguration jpc = j!=null ? j.proxy : null;
        if(jpc != null) {
            hc.getHostConfiguration().setProxy(jpc.name, jpc.port);
            if(jpc.getUserName() != null)
                hc.getState().setProxyCredentials(AuthScope.ANY,new UsernamePasswordCredentials(jpc.getUserName(),jpc.getPassword()));
        }

        int authCount=0, totalPageCount=0;  // counters for avoiding infinite loop

        HttpMethodBase m = new GetMethod(primary.filepath);
        hc.getState().addCookie(new Cookie(".oracle.com","gpw_e24",".", "/", -1, false));
        hc.getState().addCookie(new Cookie(".oracle.com","oraclelicense","accept-securebackup-cookie", "/", -1, false));
        try {
            while (true) {
                if (totalPageCount++>16) // looping too much
                    throw new IOException("Unable to find the login form");

                LOGGER.fine("Requesting " + m.getURI());
                int r = hc.executeMethod(m);
                if (r/100==3) {
                    // redirect?
                    String loc = m.getResponseHeader("Location").getValue();
                    m.releaseConnection();
                    m = new GetMethod(loc);
                    continue;
                }
                if (r!=200)
                    throw new IOException("Failed to request " + m.getURI() +" exit code="+r);

                if (m.getURI().getHost().equals("login.oracle.com")) {
                    LOGGER.fine("Appears to be a login page");
                    String resp = IOUtils.toString(m.getResponseBodyAsStream(), m.getResponseCharSet());
                    m.releaseConnection();
                    Matcher pm = Pattern.compile("<form .*?action=\"([^\"]*)\" .*?</form>", Pattern.DOTALL).matcher(resp);
                    if (!pm.find())
                        throw new IllegalStateException("Unable to find a form in the response:\n"+resp);

                    String form = pm.group();
                    PostMethod post = new PostMethod(
                            new URL(new URL(m.getURI().getURI()),pm.group(1)).toExternalForm());

                    String u = getDescriptor().getUsername();
                    Secret p = getDescriptor().getPassword();
                    if (u==null || p==null) {
                        log.hyperlink(getCredentialPageUrl(),"Oracle now requires Oracle account to download previous versions of JDK. Please specify your Oracle account username/password.\n");
                        throw new AbortException("Unable to install JDK unless a valid Oracle account username/password is provided in the system configuration.");
                    }

                    for (String fragment : form.split("<input")) {
                        String n = extractAttribute(fragment,"name");
                        String v = extractAttribute(fragment,"value");
                        if (n==null || v==null)     continue;
                        if (n.equals("ssousername"))
                            v = u;
                        if (n.equals("password")) {
                            v = p.getPlainText();
                            if (authCount++ > 3) {
                                log.hyperlink(getCredentialPageUrl(),"Your Oracle account doesn't appear valid. Please specify a valid username/password\n");
                                throw new AbortException("Unable to install JDK unless a valid username/password is provided.");
                            }
                        }
                        post.addParameter(n, v);
                    }

                    m = post;
                } else {
                    log.getLogger().println("Downloading " + m.getResponseContentLength() + " bytes");

                    // download to a temporary file and rename it in to handle concurrency and failure correctly,
                    File tmp = new File(cache.getPath()+".tmp");
                    try {
                        tmp.getParentFile().mkdirs();
                        FileOutputStream out = new FileOutputStream(tmp);
                        try {
                            IOUtils.copy(m.getResponseBodyAsStream(), out);
                        } finally {
                            out.close();
                        }

                        tmp.renameTo(cache);
                        return cache.toURL();
                    } finally {
                        tmp.delete();
                    }
                }
            }
        } finally {
            m.releaseConnection();
        }
    }

    private static String extractAttribute(String s, String name) {
        String h = name + "=\"";
        int si = s.indexOf(h);
        if (si<0)   return null;
        int ei = s.indexOf('\"',si+h.length());
        return s.substring(si+h.length(),ei);
    }

    private String getCredentialPageUrl() {
        return "/"+getDescriptor().getDescriptorUrl()+"/enterCredential";
    }

    public enum Preference {
        PRIMARY, SECONDARY, UNACCEPTABLE
    }

    /**
     * Supported platform.
     */
    public enum Platform {
        LINUX("jdk.sh"), SOLARIS("jdk.sh"), WINDOWS("jdk.exe"), OSX("jdk.dmg");

        /**
         * Choose the file name suitable for the downloaded JDK bundle.
         */
        public final String bundleFileName;

        Platform(String bundleFileName) {
            this.bundleFileName = bundleFileName;
        }

        public boolean is(String line) {
            return line.contains(name());
        }

        /**
         * Determines the platform of the given node.
         */
        public static Platform of(Node n) throws IOException,InterruptedException,DetectionFailedException {
            return n.getChannel().call(new GetCurrentPlatform());
        }

        public static Platform current() throws DetectionFailedException {
            String arch = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
            if(arch.contains("linux"))  return LINUX;
            if(arch.contains("windows"))   return WINDOWS;
            if(arch.contains("sun") || arch.contains("solaris"))    return SOLARIS;
            if(arch.contains("mac")) return OSX;
            throw new DetectionFailedException("Unknown CPU name: "+arch);
        }

        static class GetCurrentPlatform extends MasterToSlaveCallable<Platform,DetectionFailedException> {
            private static final long serialVersionUID = 1L;
            public Platform call() throws DetectionFailedException {
                return current();
            }
        }

    }

    /**
     * CPU type.
     */
    public enum CPU {
        i386, amd64, Sparc, Itanium;

        /**
         * In JDK5u3, I see platform like "Linux AMD64", while JDK6u3 refers to "Linux x64", so
         * just use "64" for locating bits.
         */
        public Preference accept(String line) {
            switch (this) {
            // these two guys are totally incompatible with everything else, so no fallback
            case Sparc:     return must(line.contains("SPARC"));
            case Itanium:   return must(line.contains("IA64"));

            // 64bit Solaris, Linux, and Windows can all run 32bit executable, so fall back to 32bit if 64bit bundle is not found
            case amd64:
                if(line.contains("SPARC") || line.contains("IA64"))  return UNACCEPTABLE;
                if(line.contains("64"))     return PRIMARY;
                return SECONDARY;
            case i386:
                if(line.contains("64") || line.contains("SPARC") || line.contains("IA64"))     return UNACCEPTABLE;
                return PRIMARY;
            }
            return UNACCEPTABLE;
        }

        private static Preference must(boolean b) {
             return b ? PRIMARY : UNACCEPTABLE;
        }

        /**
         * Determines the CPU of the given node.
         */
        public static CPU of(Node n) throws IOException,InterruptedException, DetectionFailedException {
            return n.getChannel().call(new GetCurrentCPU());
        }

        /**
         * Determines the CPU of the current JVM.
         *
         * http://lopica.sourceforge.net/os.html was useful in writing this code.
         */
        public static CPU current() throws DetectionFailedException {
            String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
            if(arch.contains("sparc"))  return Sparc;
            if(arch.contains("ia64"))   return Itanium;
            if(arch.contains("amd64") || arch.contains("86_64"))    return amd64;
            if(arch.contains("86"))    return i386;
            throw new DetectionFailedException("Unknown CPU architecture: "+arch);
        }

        static class GetCurrentCPU extends MasterToSlaveCallable<CPU,DetectionFailedException> {
            private static final long serialVersionUID = 1L;
            public CPU call() throws DetectionFailedException {
                return current();
            }
        }

    }

    /**
     * Indicates the failure to detect the OS or CPU.
     */
    private static final class DetectionFailedException extends Exception {
        private DetectionFailedException(String message) {
            super(message);
        }
    }

    public static final class JDKFamilyList {
        public JDKFamily[] data = new JDKFamily[0];
        public int version;

        public boolean isEmpty() {
            for (JDKFamily f : data) {
                if (f.releases.length>0)
                    return false;
            }
            return true;
        }

        public JDKRelease getRelease(String productCode) {
            for (JDKFamily f : data) {
                for (JDKRelease r : f.releases) {
                    if (r.matchesId(productCode))
                        return r;
                }
            }
            return null;
        }
    }

    public static final class JDKFamily {
        public String name;
        public JDKRelease[] releases;
    }

    public static final class JDKRelease {
        /**
         * the list of {@Link JDKFile}s
         */
        public JDKFile[] files;
        /**
         * the license path
         */
        public String licpath;
        /**
         * the license title
         */
        public String lictitle;
        /**
         * This maps to the former product code, like "jdk-6u13-oth-JPR"
         */
        public String name;
        /**
         * This is human readable.
         */
        public String title;

        /**
         * We used to use IDs like "jdk-6u13-oth-JPR@CDS-CDS_Developer", but Oracle switched to just "jdk-6u13-oth-JPR".
         * This method matches if the specified string matches the name, and it accepts both the old and the new format.
         */
        public boolean matchesId(String rhs) {
            return rhs!=null && (rhs.equals(name) || rhs.startsWith(name+"@"));
        }
    }

    public static final class JDKFile {
        public String filepath;
        public String name;
        public String title;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends ToolInstallerDescriptor<JDKInstaller> {
        private String username;
        private Secret password;

        public DescriptorImpl() {
            load();
        }

        public String getDisplayName() {
            return Messages.JDKInstaller_DescriptorImpl_displayName();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType==JDK.class;
        }

        public String getUsername() {
            return username;
        }

        public Secret getPassword() {
            return password;
        }

        public FormValidation doCheckId(@QueryParameter String value) {
            if (Util.fixEmpty(value) == null)
                return FormValidation.error(Messages.JDKInstaller_DescriptorImpl_doCheckId()); // improve message
            return FormValidation.ok();
        }

        /**
         * List of installable JDKs.
         * @return never null.
         */
        public List<JDKFamily> getInstallableJDKs() throws IOException {
            return Arrays.asList(JDKList.all().get(JDKList.class).toList().data);
        }

        public FormValidation doCheckAcceptLicense(@QueryParameter boolean value) {
            if (username==null || password==null)
                return FormValidation.errorWithMarkup(Messages.JDKInstaller_RequireOracleAccount(Stapler.getCurrentRequest().getContextPath()+'/'+getDescriptorUrl()+"/enterCredential"));
            if (value) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.JDKInstaller_DescriptorImpl_doCheckAcceptLicense());
            }
        }

        /**
         * Submits the Oracle account username/password.
         */
        public HttpResponse doPostCredential(@QueryParameter String username, @QueryParameter String password) throws IOException, ServletException {
            this.username = username;
            this.password = Secret.fromString(password);
            save();
            return HttpResponses.redirectTo("credentialOK");
        }
    }

    /**
     * JDK list.
     */
    @Extension
    public static final class JDKList extends Downloadable {
        public JDKList() {
            super(JDKInstaller.class);
        }

        public JDKFamilyList toList() throws IOException {
            JSONObject d = getData();
            if(d==null) return new JDKFamilyList();
            return (JDKFamilyList)JSONObject.toBean(d,JDKFamilyList.class);
        }

        /**
         * @{inheritDoc}
         */
        @Override
        public JSONObject reduce (List<JSONObject> jsonObjectList) {
            List<JDKFamily> reducedFamilies = new LinkedList<>();
            int version = 0;
            JsonConfig jsonConfig = new JsonConfig();
            jsonConfig.registerPropertyExclusion(JDKFamilyList.class, "empty");
            jsonConfig.setRootClass(JDKFamilyList.class);
            //collect all JDKFamily objects from the multiple json objects
            for (JSONObject jsonJdkFamilyList : jsonObjectList) {
                JDKFamilyList jdkFamilyList = (JDKFamilyList)JSONObject.toBean(jsonJdkFamilyList, jsonConfig);
                if (version == 0) {
                    //we set as version the version of the first update center
                    version = jdkFamilyList.version;
                }
                JDKFamily[] jdkFamilies = jdkFamilyList.data;
                reducedFamilies.addAll(Arrays.asList(jdkFamilies));
            }
            //we  iterate on the list and reduce it until there are no more duplicates
            //this could be made recursive
            while (hasDuplicates(reducedFamilies, "name")) {
                //create a temporary list to store the tmp result
                List<JDKFamily> tmpReducedFamilies = new LinkedList<>();
                //we need to skip the processed families
                boolean processed [] = new boolean[reducedFamilies.size()];
                for (int i = 0; i < reducedFamilies.size(); i ++ ) {
                    if (processed [i] == true) {
                        continue;
                    }
                    JDKFamily data1 = reducedFamilies.get(i);
                    boolean hasDuplicate = false;
                    for (int j = i + 1; j < reducedFamilies.size(); j ++ ) {
                        JDKFamily data2 = reducedFamilies.get(j);
                        //if we found a duplicate we need to merge the families
                        if (data1.name.equals(data2.name)) {
                            hasDuplicate = true;
                            processed [j] = true;
                            JDKFamily reducedData = reduceData(data1.name, new LinkedList<JDKRelease>(Arrays.asList(data1.releases)), new LinkedList<JDKRelease>(Arrays.asList(data2.releases)));
                            tmpReducedFamilies.add(reducedData);
                            //after the first duplicate has been found we break the loop since the duplicates are
                            //processed two by two
                            break;
                        }
                    }
                    //if no duplicate has been found we just insert the whole family in the tmp list
                    if (!hasDuplicate) {
                        tmpReducedFamilies.add(data1);
                    }
                }
                reducedFamilies = tmpReducedFamilies;
            }
            JDKFamilyList jdkFamilyList = new JDKFamilyList();
            jdkFamilyList.version = version;
            jdkFamilyList.data = new JDKFamily[reducedFamilies.size()];
            reducedFamilies.toArray(jdkFamilyList.data);
            JSONObject reducedJdkFamilyList = JSONObject.fromObject(jdkFamilyList, jsonConfig);
            //return the list with no duplicates
            return reducedJdkFamilyList;
        }

        private JDKFamily reduceData(String name, List<JDKRelease> releases1, List<JDKRelease> releases2) {
            LinkedList<JDKRelease> reducedReleases = new LinkedList<>();
            for (Iterator<JDKRelease> iterator = releases1.iterator(); iterator.hasNext(); ) {
                JDKRelease release1 = iterator.next();
                boolean hasDuplicate = false;
                for (Iterator<JDKRelease> iterator2 = releases2.iterator(); iterator2.hasNext(); ) {
                    JDKRelease release2 = iterator2.next();
                    if (release1.name.equals(release2.name)) {
                        hasDuplicate = true;
                        JDKRelease reducedRelease = reduceReleases(release1, new LinkedList<JDKFile>(Arrays.asList(release1.files)), new LinkedList<JDKFile>(Arrays.asList(release2.files)));
                        iterator2.remove();
                        reducedReleases.add(reducedRelease);
                        //we assume that in one release list there are no duplicates so we stop at the first one
                        break;
                    }
                }
                if (!hasDuplicate) {
                    reducedReleases.add(release1);
                }
            }
            reducedReleases.addAll(releases2);
            JDKFamily reducedFamily = new JDKFamily();
            reducedFamily.name = name;
            reducedFamily.releases = new JDKRelease[reducedReleases.size()];
            reducedReleases.toArray(reducedFamily.releases);
            return reducedFamily;
        }

        private JDKRelease reduceReleases(JDKRelease release, List<JDKFile> files1, List<JDKFile> files2) {
            LinkedList<JDKFile> reducedFiles = new LinkedList<>();
            for (Iterator<JDKFile> iterator1 = files1.iterator(); iterator1.hasNext(); ) {
                JDKFile file1 = iterator1.next();
                for (Iterator<JDKFile> iterator2 = files2.iterator(); iterator2.hasNext(); ) {
                    JDKFile file2 = iterator2.next();
                    if (file1.name.equals(file2.name)) {
                        iterator2.remove();
                        //we assume the in one file list there are no duplicates so we break after we find the
                        //first match
                        break;
                    }
                }
            }
            reducedFiles.addAll(files1);
            reducedFiles.addAll(files2);

            JDKRelease jdkRelease = new JDKRelease();
            jdkRelease.files = new JDKFile[reducedFiles.size()];
            reducedFiles.toArray(jdkRelease.files);
            jdkRelease.name = release.name;
            jdkRelease.licpath = release.licpath;
            jdkRelease.lictitle = release.lictitle;
            jdkRelease.title = release.title;
            return jdkRelease;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JDKInstaller.class.getName());
}
