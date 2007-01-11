package hudson.scm;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormFieldValidator;
import org.apache.commons.digester.Digester;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Subversion.
 *
 * Check http://svn.collab.net/repos/svn/trunk/subversion/svn/schema/ for
 * various output formats.
 *
 * @author Kohsuke Kawaguchi
 */
public class SubversionSCM extends AbstractCVSFamilySCM {
    private final String modules;
    private boolean useUpdate;
    private String username;
    private String otherOptions;

    SubversionSCM( String modules, boolean useUpdate, String username, String otherOptions ) {
        StringBuilder normalizedModules = new StringBuilder();
        StringTokenizer tokens = new StringTokenizer(modules);
        while(tokens.hasMoreTokens()) {
            if(normalizedModules.length()>0)    normalizedModules.append(' ');
            String m = tokens.nextToken();
            if(m.endsWith("/"))
                // the normalized name is always without the trailing '/'
                m = m.substring(0,m.length()-1);
            normalizedModules.append(m);
       }

        this.modules = normalizedModules.toString();
        this.useUpdate = useUpdate;
        this.username = nullify(username);
        this.otherOptions = nullify(otherOptions);
    }

    /**
     * Whitespace-separated list of SVN URLs that represent
     * modules to be checked out.
     */
    public String getModules() {
        return modules;
    }

    public boolean isUseUpdate() {
        return useUpdate;
    }

    public String getUsername() {
        return username;
    }

    public String getOtherOptions() {
        return otherOptions;
    }

    private Collection<String> getModuleDirNames() {
        List<String> dirs = new ArrayList<String>();
        StringTokenizer tokens = new StringTokenizer(modules);
        while(tokens.hasMoreTokens()) {
            dirs.add(getLastPathComponent(tokens.nextToken()));
        }
        return dirs;
    }

    private boolean calcChangeLog(AbstractBuild<?,?> build, File changelogFile, Launcher launcher, BuildListener listener) throws IOException {
        if(build.getPreviousBuild()==null) {
            // nothing to compare against
            return createEmptyChangeLog(changelogFile, listener, "log");
        }

        PrintStream logger = listener.getLogger();

        Map<String,Integer> previousRevisions = parseRevisionFile(build.getPreviousBuild());
        Map<String,Integer> thisRevisions     = parseRevisionFile(build);

        Map<String,String> env = createEnvVarMap(true);

        boolean changelogFileCreated = false;

        for( String module : getModuleDirNames() ) {
            Integer prevRev = previousRevisions.get(module);
            if(prevRev==null) {
                logger.println("no revision recorded for "+module+" in the previous build");
                continue;
            }
            Integer thisRev = thisRevisions.get(module);
            if(thisRev!=null && thisRev.equals(prevRev)) {
                logger.println("no change for "+module+" since the previous build");
                continue;
            }

            // TODO: this seems to clobber previously recorded changes when there are multiple modules
            String cmd = DESCRIPTOR.getSvnExe()+" log -v --xml --non-interactive -r "+(prevRev+1)+":BASE "+module;
            OutputStream os = new BufferedOutputStream(new FileOutputStream(changelogFile));
            changelogFileCreated = true;
            try {
                int r = launcher.launch(cmd,env,os,build.getProject().getWorkspace()).join();
                pause(listener);
                if(r!=0) {
                    listener.fatalError("revision check failed");
                    // report the output
                    FileInputStream log = new FileInputStream(changelogFile);
                    try {
                        Util.copyStream(log,listener.getLogger());
                    } finally {
                        log.close();
                    }
                    return false;
                }
            } finally {
                os.close();
            }
        }

        if(!changelogFileCreated)
            createEmptyChangeLog(changelogFile, listener, "log");

        return true;
    }

    /*package*/ static Map<String,Integer> parseRevisionFile(AbstractBuild build) throws IOException {
        Map<String,Integer> revisions = new HashMap<String,Integer>(); // module -> revision
        {// read the revision file of the last build
            File file = getRevisionFile(build);
            if(!file.exists())
                // nothing to compare against
                return revisions;

            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while((line=br.readLine())!=null) {
                int index = line.indexOf('/');
                if(index<0) {
                    continue;   // invalid line?
                }
                try {
                    revisions.put(line.substring(0,index), Integer.parseInt(line.substring(index+1)));
                } catch (NumberFormatException e) {
                    // perhaps a corrupted line. ignore
                }
            }
        }

        return revisions;
    }

    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        boolean result;

        if(useUpdate && isUpdatable(workspace,launcher,listener)) {
            result = update(launcher,workspace,listener);
            if(!result)
                return false;
        } else {
            workspace.deleteContents();
            StringTokenizer tokens = new StringTokenizer(modules);
            while(tokens.hasMoreTokens()) {
                ArgumentListBuilder cmd = new ArgumentListBuilder();
                cmd.add(DESCRIPTOR.getSvnExe(),"co",/*"-q",*/"--non-interactive");
                if(username!=null)
                    cmd.add("--username",username);
                if(otherOptions!=null)
                    cmd.add(Util.tokenize(otherOptions));
                cmd.add(tokens.nextToken());

                result = run(launcher,cmd,listener,workspace);
                pause(listener);
                if(!result)
                    return false;
            }
        }

        // write out the revision file
        PrintWriter w = new PrintWriter(new FileOutputStream(getRevisionFile(build)));
        try {
            Map<String,SvnInfo> revMap = buildRevisionMap(workspace,launcher,listener);
            for (Entry<String,SvnInfo> e : revMap.entrySet()) {
                w.println( e.getKey() +'/'+ e.getValue().revision );
            }
        } finally {
            w.close();
        }

        return calcChangeLog(build, changelogFile, launcher, listener);
    }

    /**
     * Output from "svn info" command.
     */
    public static class SvnInfo {
        /** The remote URL of this directory */
        String url;
        /** Current workspace revision. */
        int revision = -1;

        private SvnInfo() {}

        /**
         * Returns true if this object is fully populated.
         */
        public boolean isComplete() {
            return url!=null && revision!=-1;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public void setRevision(int revision) {
            this.revision = revision;
        }

        /**
         * Executes "svn info" command and returns the parsed output
         *
         * @param subject
         *      The target to run "svn info". Either local path or remote URL.
         */
        public static SvnInfo parse(String subject, Map<String,String> env, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException {
            String cmd = DESCRIPTOR.getSvnExe()+" info --xml "+subject;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            int r = launcher.launch(cmd,env,baos,workspace).join();
            pause(listener);
            if(r!=0) {
                // failed. to allow user to diagnose the problem, send output to log
                listener.getLogger().write(baos.toByteArray());
                throw new IOException("svn info failed");
            }

            if(dumpSvnInfo) {
                // dump the output for debugging
                listener.getLogger().write(baos.toByteArray());
            }

            SvnInfo info = new SvnInfo();

            Digester digester = new Digester();
            digester.push(info);

            digester.addBeanPropertySetter("info/entry/url");
            digester.addSetProperties("info/entry/commit","revision","revision");  // set attributes. in particular @revision

            try {
                digester.parse(new ByteArrayInputStream(baos.toByteArray()));
            } catch (SAXException e) {
                // failed. to allow user to diagnose the problem, send output to log
                listener.getLogger().write(baos.toByteArray());
                e.printStackTrace(listener.fatalError("Failed to parse Subversion output"));
                throw new IOException("Unable to parse svn info output");
            }

            if(!info.isComplete())
                throw new IOException("No revision in the svn info output");

            return info;
        }

    }

    /**
     * Checks .svn files in the workspace and finds out revisions of the modules
     * that the workspace has.
     *
     * @return
     *      null if the parsing somehow fails. Otherwise a map from module names to revisions.
     */
    private Map<String,SvnInfo> buildRevisionMap(FilePath workspace, Launcher launcher, TaskListener listener) throws IOException {
        PrintStream logger = listener.getLogger();

        Map<String/*module name*/,SvnInfo> revisions = new HashMap<String,SvnInfo>();

        Map<String,String> env = createEnvVarMap(false);

        // invoke the "svn info"
        for( String module : getModuleDirNames() ) {
            // parse the output
            SvnInfo info = SvnInfo.parse(module,env,workspace,launcher,listener);
            revisions.put(module,info);
            logger.println("Revision:"+info.revision);
        }

        return revisions;
    }

    /**
     * Gets the file that stores the revision.
     */
    private static File getRevisionFile(AbstractBuild build) {
        return new File(build.getRootDir(),"revision.txt");
    }

    public boolean update(Launcher launcher, FilePath remoteDir, BuildListener listener) throws IOException {
        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(DESCRIPTOR.getSvnExe(), "update", /*"-q",*/ "--non-interactive");

        if(username!=null)
            cmd.add(" --username ",username);
        if(otherOptions!=null)
            cmd.add(Util.tokenize(otherOptions));

        StringTokenizer tokens = new StringTokenizer(modules);
        while(tokens.hasMoreTokens()) {
            boolean result = run(launcher, cmd, listener, new FilePath(remoteDir, getLastPathComponent(tokens.nextToken())));
            pause(listener);
            if(!result)
                return false;
        }
        return true;
    }

    private static void pause(TaskListener listener) {
        try {
            if(pauseBetweenInvocation<=0)   return; // do nothing
            listener.getLogger().println("Pausing "+pauseBetweenInvocation+"ms");
            Thread.sleep(pauseBetweenInvocation);
        } catch (InterruptedException e) {
            listener.getLogger().println("aborted");
        }
    }

    /**
     * Returns true if we can use "svn update" instead of "svn checkout"
     */
    private boolean isUpdatable(FilePath workspace,Launcher launcher,BuildListener listener) {
        StringTokenizer tokens = new StringTokenizer(modules);
        while(tokens.hasMoreTokens()) {
            String url = tokens.nextToken();
            String moduleName = getLastPathComponent(url);
            FilePath module = workspace.child(moduleName);

            try {
                SvnInfo svnInfo = SvnInfo.parse(moduleName, createEnvVarMap(false), workspace, launcher, listener);
                if(!svnInfo.url.equals(url)) {
                    listener.getLogger().println("Checking out a fresh workspace because the workspace is not "+url);
                    return false;
                }
            } catch (IOException e) {
                listener.getLogger().println("Checking out a fresh workspace because Hudson failed to detect the current workspace "+module);
                e.printStackTrace(listener.error(e.getMessage()));
                return false;
            }
        }
        return true;
    }

    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException {
        // current workspace revision
        Map<String,SvnInfo> wsRev = buildRevisionMap(workspace,launcher,listener);

        Map<String,String> env = createEnvVarMap(false);

        // check the corresponding remote revision
        for (SvnInfo localInfo : wsRev.values()) {
            SvnInfo remoteInfo = SvnInfo.parse(localInfo.url,env,workspace,launcher,listener);
            listener.getLogger().println("Revision:"+remoteInfo.revision);
            if(remoteInfo.revision > localInfo.revision)
                return true;    // change found
        }

        return false; // no change
    }

    public ChangeLogParser createChangeLogParser() {
        return new SubversionChangeLogParser();
    }


    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public void buildEnvVars(Map<String,String> env) {
        // no environment variable
    }

    public FilePath getModuleRoot(FilePath workspace) {
        String s;

        // if multiple URLs are specified, pick the first one
        int idx = modules.indexOf(' ');
        if(idx>=0)  s = modules.substring(0,idx);
        else        s = modules;

        return workspace.child(getLastPathComponent(s));
    }

    private String getLastPathComponent(String s) {
        String[] tokens = s.split("/");
        return tokens[tokens.length-1]; // return the last token
    }

    static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<SCM> {
        /**
         * Path to <tt>svn.exe</tt>. Null to default.
         */
        private String svnExe;

        DescriptorImpl() {
            super(SubversionSCM.class);
            load();
        }

        protected void convert(Map<String, Object> oldPropertyBag) {
            svnExe = (String)oldPropertyBag.get("svn_exe");
        }

        public String getDisplayName() {
            return "Subversion";
        }

        public SCM newInstance(StaplerRequest req) {
            return new SubversionSCM(
                req.getParameter("svn_modules"),
                req.getParameter("svn_use_update")!=null,
                req.getParameter("svn_username"),
                req.getParameter("svn_other_options")
            );
        }

        public String getSvnExe() {
            String value = svnExe;
            if(value==null)
                value = "svn";
            return value;
        }

        public void setSvnExe(String value) {
            svnExe = value;
            save();
        }

        public boolean configure( StaplerRequest req ) {
            svnExe = req.getParameter("svn_exe");
            save();
            return true;
        }

        /**
         * Returns the Subversion version information.
         *
         * @return
         *      null if failed to obtain.
         */
        public Version version(Launcher l, String svnExe) {
            try {
                if(svnExe==null || svnExe.equals(""))    svnExe="svn";

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                l.launch(new String[]{svnExe,"--version"},new String[0],out,null).join();

                // parse the first line for version
                BufferedReader r = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(out.toByteArray())));
                String line;
                while((line = r.readLine())!=null) {
                    Matcher m = SVN_VERSION.matcher(line);
                    if(m.matches())
                        return new Version(Integer.parseInt(m.group(2)), m.group(1));
                }

                // ancient version of subversions didn't have the fixed version number line.
                // or maybe something else is going wrong.
                LOGGER.log(Level.WARNING, "Failed to parse the first line from svn output: "+line);
                return new Version(0,"(unknown)");
            } catch (IOException e) {
                // Stack trace likely to be overkill for a problem that isn't necessarily a problem at all:
                LOGGER.log(Level.WARNING, "Failed to check svn version: {0}", e.toString());
                return null; // failed to obtain
            }
        }

        // web methods

        public void doVersionCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            // this method runs a new process, so it needs to be protected
            new FormFieldValidator(req,rsp,true) {
                protected void check() throws IOException, ServletException {
                    String svnExe = request.getParameter("exe");

                    Version v = version(new Launcher.LocalLauncher(TaskListener.NULL),svnExe);
                    if(v==null) {
                        error("Failed to check subversion version info. Is this a valid path?");
                        return;
                    }
                    if(v.isOK()) {
                        ok();
                    } else {
                        error("Version "+v.versionId+" found, but 1.3.0 is required");
                    }
                }
            }.process();
        }
    }

    public static final class Version {
        private final int revision;
        private String versionId;

        public Version(int revision, String versionId) {
            this.revision = revision;
            this.versionId = versionId;
        }

        /**
         * Repository revision ID of this build.
         */
        public int getRevision() {
            return revision;
        }

        /**
         * Human-readable version string.
         */
        public String getVersionId() {
            return versionId;
        }

        /**
         * We use "svn info --xml", which is new in 1.3.0
         */
        public boolean isOK() {
            return revision>=17949;
        }
    }

    private static final Pattern SVN_VERSION = Pattern.compile("svn, .+ ([0-9.]+) \\(r([0-9]+)\\)");

    private static final Logger LOGGER = Logger.getLogger(SubversionSCM.class.getName());

    /**
     * Debug switch to dump "svn info" output, to troubleshoot issue #167.
     */
    public static boolean dumpSvnInfo = false;

    /**
     * Debug switch that causes Hudson to wait between svn invocation.
     * This is a temporary change to trouble-shoot issue #167.
     */
    public static int pauseBetweenInvocation = 0;
}
