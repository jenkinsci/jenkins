/*
 * The MIT License
 *
 * Copyright (c) 2009, Sun Microsystems, Inc.
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
import hudson.Util;
import hudson.util.FormValidation;
import hudson.util.ArgumentListBuilder;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.DownloadService.Downloadable;
import hudson.model.JDK;
import static hudson.tools.JDKInstaller.Preference.*;
import hudson.remoting.Callable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullWriter;
import org.w3c.tidy.Tidy;
import org.dom4j.io.DOMReader;
import org.dom4j.Document;
import org.dom4j.Element;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.PrintWriter;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONObject;

/**
 * Install JDKs from java.sun.com.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.305
 */
public class JDKInstaller extends ToolInstaller {
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
                out.println("Unable to perform installation until the license is accepted.");
                return expectedLocation;
            }
            // already installed?
            FilePath marker = expectedLocation.child(".installedByHudson");
            if(marker.exists())
                return expectedLocation;
            expectedLocation.mkdirs();

            Platform p = Platform.of(node);
            URL url = locate(log, p, CPU.of(node));

            out.println("Downloading "+url);
            FilePath file = expectedLocation.child(fileName(p));
            file.copyFrom(url);

            out.println("Installing "+file);
            switch (p) {
            case LINUX:
            case SOLARIS:
                file.chmod(0755);
                if(node.createLauncher(log).launch().cmds(file.getRemote(),"-noregister")
                    .stdin(new ByteArrayInputStream("yes".getBytes())).stdout(out).pwd(expectedLocation).join()!=0)
                    throw new AbortException("Failed to install JDK");

                // JDK creates its own sub-directory, so pull them up
                List<FilePath> paths = expectedLocation.list(new JdkFinder());
                if(paths.size()!=1)
                    throw new AbortException("Failed to find the extracted JDKs: "+paths);

                // remove the intermediate directory 
                paths.get(0).moveAllChildrenTo(expectedLocation);
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
                // see
                //
                
                FilePath logFile = node.getRootPath().createTempFile("jdk-install",".log");
                // JDK6u13 doesn't like path representation like "/tmp/foo", so make it a strict Windows format
                String normalizedPath = expectedLocation.absolutize().getRemote();

                ArgumentListBuilder args = new ArgumentListBuilder();
                args.add(file.getRemote());
                args.add("/s");
                args.add("/v/qn REBOOT=Suppress INSTALLDIR="+normalizedPath+" /L "+logFile.getRemote());
                
                if(node.createLauncher(log).launch().cmds(args).stdout(out).pwd(expectedLocation).join()!=0) {
                    out.println("Failed to install JDK");
                    // log file is in UTF-16
                    InputStreamReader in = new InputStreamReader(logFile.read(), "UTF-16");
                    try {
                        IOUtils.copy(in,new OutputStreamWriter(out));
                    } finally {
                        in.close();
                    }
                    throw new AbortException();
                }

                logFile.delete();

                break;
            }

            // successfully installed
            file.delete();
            marker.touch(System.currentTimeMillis());

        } catch (DetectionFailedException e) {
            out.println("JDK installation skipped: "+e.getMessage());
        }

        return expectedLocation;
    }

    /**
     * Choose the file name suitable for the downloaded JDK bundle.
     */
    private String fileName(Platform p) {
        switch (p) {
        case LINUX:
        case SOLARIS:
            return "jdk.sh";
        case WINDOWS:
            return "jdk.exe";
        }
        throw new AssertionError();
    }

    /**
     * Finds the directory that JDK has created.
     */
    private static class JdkFinder implements FileFilter, Serializable {
        private static final long serialVersionUID = 1L;
        public boolean accept(File f) {
            return f.isDirectory() && f.getName().startsWith("jdk");
        }
    };

    /**
     * Performs a license click through and obtains the one-time URL for downloading bits.
     *
     */
    /*package*/ URL locate(TaskListener log, Platform platform, CPU cpu) throws IOException {
        HttpURLConnection con = locateStage1(platform, cpu);
        String page = IOUtils.toString(con.getInputStream());
        return locateStage2(log, page);
    }

    private HttpURLConnection locateStage1(Platform platform, CPU cpu) throws IOException {
        URL url = new URL("https://cds.sun.com/is-bin/INTERSHOP.enfinity/WFS/CDS-CDS_Developer-Site/en_US/-/USD/ViewProductDetail-Start?ProductRef="+id);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        String cookie = con.getHeaderField("Set-Cookie");
        LOGGER.fine("Cookie="+cookie);

        Tidy tidy = new Tidy();
        tidy.setErrout(new PrintWriter(new NullWriter()));
        DOMReader domReader = new DOMReader();
        Document dom = domReader.read(tidy.parseDOM(con.getInputStream(), null));

        Element form=null;
        for (Element e : (List<Element>)dom.selectNodes("//form")) {
            String action = e.attributeValue("action");
            LOGGER.fine("Found form:"+action);
            if(action.contains("ViewFilteredProducts")) {
                form = e;
                break;
            }
        }

        con = (HttpURLConnection) new URL(form.attributeValue("action")).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Cookie",cookie);
        con.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        PrintStream os = new PrintStream(con.getOutputStream());

        // select platform
        String primary=null,secondary=null;
        Element p = (Element)form.selectSingleNode(".//select[@id='dnld_platform']");
        for (Element opt : (List<Element>)p.elements("option")) {
            String value = opt.attributeValue("value");
            String vcap = value.toUpperCase(Locale.ENGLISH);
            if(!platform.is(vcap))  continue;
            switch (cpu.accept(vcap)) {
            case PRIMARY:   primary = value;break;
            case SECONDARY: secondary=value;break;
            case UNACCEPTABLE:  break;
            }
        }
        if(primary==null)   primary=secondary;
        if(primary==null)
            throw new AbortException("Couldn't find the right download for "+platform+" and "+ cpu +" combination");
        os.print(p.attributeValue("name")+'='+primary);
        LOGGER.fine("Platform choice:"+primary);

        // select language
        Element l = (Element)form.selectSingleNode(".//select[@id='dnld_language']");
        os.print("&"+l.attributeValue("name")+"="+l.element("option").attributeValue("value"));

        // the rest
        for (Element e : (List<Element>)form.selectNodes(".//input")) {
            os.print('&');
            os.print(e.attributeValue("name"));
            os.print('=');
            String value = e.attributeValue("value");
            if(value==null)
                os.print("on"); // assume this is a checkbox
            else
                os.print(URLEncoder.encode(value,"UTF-8"));
        }
        os.close();
        return con;
    }

    private URL locateStage2(TaskListener log, String page) throws MalformedURLException {
        Pattern HREF = Pattern.compile("<a href=\"(http://cds.sun.com/[^\"]+/VerifyItem-Start[^\"]+)\"");
        Matcher m = HREF.matcher(page);
        // this page contains a missing --> that confuses dom4j/jtidy

        log.getLogger().println("Choosing the download bundle");
        List<String> urls = new ArrayList<String>();

        while(m.find()) {
            String url = m.group(1);
            LOGGER.fine("Considering a download link:"+ url);

            // still more options to choose from.
            // avoid rpm bundles, and avoid tar.Z bundle
            if(url.contains("rpm"))  continue;
            if(url.contains("tar.Z"))  continue;
            // sparcv9 bundle is add-on to the sparc bundle, so just download 32bit sparc bundle, even on 64bit system
            if(url.contains("sparcv9"))  continue;

            urls.add(url);
            LOGGER.fine("Found a download candidate: "+ url);
        }

        // prefer the first match because sometimes "optional downloads" follow the main bundle
        return new URL(urls.get(0));
    }

    public enum Preference {
        PRIMARY, SECONDARY, UNACCEPTABLE
    }

    /**
     * Supported platform.
     */
    public enum Platform {
        LINUX, SOLARIS, WINDOWS;

        public boolean is(String line) {
            return line.contains(name());
        }

        /**
         * Determines the platform of the given node.
         */
        public static Platform of(Node n) throws IOException,InterruptedException,DetectionFailedException {
            return n.toComputer().getChannel().call(new Callable<Platform,DetectionFailedException>() {
                public Platform call() throws DetectionFailedException {
                    return current();
                }
            });
        }

        public static Platform current() throws DetectionFailedException {
            String arch = System.getProperty("os.name").toLowerCase();
            if(arch.contains("linux"))  return LINUX;
            if(arch.contains("windows"))   return WINDOWS;
            if(arch.contains("sun") || arch.contains("solaris"))    return SOLARIS;
            throw new DetectionFailedException("Unknown CPU name: "+arch);
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
            case Itanium:   return must(line.contains("ITANIUM"));

            // 64bit Solaris, Linux, and Windows can all run 32bit executable, so fall back to 32bit if 64bit bundle is not found
            case amd64:
                if(line.contains("64"))     return PRIMARY;
                if(line.contains("SPARC") || line.contains("ITANIUM"))  return UNACCEPTABLE;
                return SECONDARY;
            case i386:
                if(line.contains("64") || line.contains("SPARC") || line.contains("ITANIUM"))     return UNACCEPTABLE;
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
            return n.toComputer().getChannel().call(new Callable<CPU,DetectionFailedException>() {
                public CPU call() throws DetectionFailedException {
                    return current();
                }
            });
        }

        /**
         * Determines the CPU of the current JVM.
         *
         * http://lopica.sourceforge.net/os.html was useful in writing this code.
         */
        public static CPU current() throws DetectionFailedException {
            String arch = System.getProperty("os.arch").toLowerCase();
            if(arch.contains("sparc"))  return Sparc;
            if(arch.contains("ia64"))   return Itanium;
            if(arch.contains("amd64") || arch.contains("86_64"))    return amd64;
            if(arch.contains("86"))    return i386;
            throw new DetectionFailedException("Unknown CPU architecture: "+arch);
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
        public JDKFamily[] jdks = new JDKFamily[0];
    }

    public static final class JDKFamily {
        public String name;
        public InstallableJDK[] list;
    }

    public static final class InstallableJDK {
        public String name;
        /**
         * Product code.
         */
        public String id;
    }

    @Extension
    public static final class DescriptorImpl extends ToolInstallerDescriptor<JDKInstaller> {
        public String getDisplayName() {
            return Messages.JDKInstaller_DescriptorImpl_displayName();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType==JDK.class;
        }

        public FormValidation doCheckId(@QueryParameter String value) {
            if (Util.fixEmpty(value) == null) {
                return FormValidation.error(Messages.JDKInstaller_DescriptorImpl_doCheckId()); // improve message
            } else {
                // XXX further checks? 
                return FormValidation.ok();
            }
        }

        /**
         * List of installable JDKs.
         * @return never null.
         */
        public List<JDKFamily> getInstallableJDKs() throws IOException {
            return Arrays.asList(JDKList.all().get(JDKList.class).toList().jdks);
        }

        public FormValidation doCheckAcceptLicense(@QueryParameter boolean value) {
            if (value) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.JDKInstaller_DescriptorImpl_doCheckAcceptLicense()); 
            }
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
    }

    private static final Logger LOGGER = Logger.getLogger(JDKInstaller.class.getName());
}
