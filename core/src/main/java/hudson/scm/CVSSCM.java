package hudson.scm;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import static hudson.Util.fixEmpty;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ModelObject;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.org.apache.tools.ant.taskdefs.cvslib.ChangeLogTask;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.util.ArgumentListBuilder;
import hudson.util.ForkOutputStream;
import hudson.util.FormFieldValidator;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Expand;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
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
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CVS.
 *
 * <p>
 * I couldn't call this class "CVS" because that would cause the view folder name
 * to collide with CVS control files.
 *
 * <p>
 * This object gets shipped to the remote machine to perform some of the work,
 * so it implements {@link Serializable}.
 *
 * @author Kohsuke Kawaguchi
 */
public class CVSSCM extends AbstractCVSFamilySCM implements Serializable {
    /**
     * CVSSCM connection string.
     */
    private String cvsroot;

    /**
     * Module names.
     *
     * This could be a whitespace-separate list of multiple modules.
     * Modules could be either directories or files. 
     */
    private String module;

    private String branch;

    private String cvsRsh;

    private boolean canUseUpdate;

    /**
     * True to avoid creating a sub-directory inside the workspace.
     * (Works only when there's just one module.)
     */
    private boolean flatten;


    public CVSSCM(String cvsroot, String module,String branch,String cvsRsh,boolean canUseUpdate, boolean flatten) {
        this.cvsroot = cvsroot;
        this.module = module.trim();
        this.branch = nullify(branch);
        this.cvsRsh = nullify(cvsRsh);
        this.canUseUpdate = canUseUpdate;
        this.flatten = flatten && module.indexOf(' ')==-1;
    }

    public String getCvsRoot() {
        return cvsroot;
    }

    /**
     * If there are multiple modules, return the module directory of the first one.
     * @param workspace
     */
    public FilePath getModuleRoot(FilePath workspace) {
        if(flatten)
            return workspace;

        int idx = module.indexOf(' ');
        if(idx>=0)  return workspace.child(module.substring(0,idx));
        else        return workspace.child(module);
    }

    public ChangeLogParser createChangeLogParser() {
        return new CVSChangeLogParser();
    }

    public String getAllModules() {
        return module;
    }

    /**
     * Branch to build. Null to indicate the trunk.
     */
    public String getBranch() {
        return branch;
    }

    public String getCvsRsh() {
        return cvsRsh;
    }

    public boolean getCanUseUpdate() {
        return canUseUpdate;
    }

    public boolean isFlatten() {
        return flatten;
    }

    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath dir, TaskListener listener) throws IOException, InterruptedException {
        List<String> changedFiles = update(true, launcher, dir, listener, new Date());

        return changedFiles!=null && !changedFiles.isEmpty();
    }

    private void configureDate(ArgumentListBuilder cmd, Date date) { // #192
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC")); // #209
        cmd.add("-D", df.format(date));
    }

    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath dir, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        List<String> changedFiles = null; // files that were affected by update. null this is a check out

        if(canUseUpdate && isUpdatable(dir)) {
            changedFiles = update(false, launcher, dir, listener, build.getTimestamp().getTime());
            if(changedFiles==null)
                return false;   // failed
        } else {
            dir.deleteContents();

            ArgumentListBuilder cmd = new ArgumentListBuilder();
            // TODO: debug option to make it verbose
            cmd.add("cvs","-Q","-z9","-d",cvsroot,"co");
            if(branch!=null)
                cmd.add("-r",branch);
            if(flatten)
                cmd.add("-d",dir.getName());
            configureDate(cmd, build.getTimestamp().getTime());
            cmd.addTokenized(module);

            if(!run(launcher,cmd,listener, flatten ? dir.getParent() : dir))
                return false;
        }

        // archive the workspace to support later tagging
        File archiveFile = getArchiveFile(build);
        final OutputStream os = new RemoteOutputStream(new FileOutputStream(archiveFile));
        
        build.getProject().getWorkspace().act(new FileCallable<Void>() {
            public Void invoke(File ws, VirtualChannel channel) throws IOException {
                ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os));

                if(flatten) {
                    archive(ws, module, zos);
                } else {
                    StringTokenizer tokens = new StringTokenizer(module);
                    while(tokens.hasMoreTokens()) {
                        String m = tokens.nextToken();
                        File mf = new File(ws, m);

                        if(!mf.isDirectory()) {
                            // this module is just a file, say "foo/bar.txt".
                            // to record "foo/CVS/*", we need to start by archiving "foo".
                            m = m.substring(0,m.lastIndexOf('/'));
                            mf = mf.getParentFile();
                        }
                        archive(mf,m,zos);
                    }
                }
                zos.close();
                return null;
            }
        });

        // contribute the tag action
        build.getActions().add(new TagAction(build));

        return calcChangeLog(build, changedFiles, changelogFile, listener);
    }

    /**
     * Returns the file name used to archive the build.
     */
    private static File getArchiveFile(AbstractBuild build) {
        return new File(build.getRootDir(),"workspace.zip");
    }

    /**
     * Archives all the CVS-controlled files in {@code dir}.
     *
     * @param relPath
     *      The path name in ZIP to store this directory with.
     */
    private void archive(File dir,String relPath,ZipOutputStream zos) throws IOException {
        Set<String> knownFiles = new HashSet<String>();
        // see http://www.monkey.org/openbsd/archive/misc/9607/msg00056.html for what Entries.Log is for
        parseCVSEntries(new File(dir,"CVS/Entries"),knownFiles);
        parseCVSEntries(new File(dir,"CVS/Entries.Log"),knownFiles);
        parseCVSEntries(new File(dir,"CVS/Entries.Extra"),knownFiles);
        boolean hasCVSdirs = !knownFiles.isEmpty();
        knownFiles.add("CVS");

        File[] files = dir.listFiles();
        if(files==null)
            throw new IOException("No such directory exists. Did you specify the correct branch?: "+dir);

        for( File f : files ) {
            String name = relPath+'/'+f.getName();
            if(f.isDirectory()) {
                if(hasCVSdirs && !knownFiles.contains(f.getName())) {
                    // not controlled in CVS. Skip.
                    // but also make sure that we archive CVS/*, which doesn't have CVS/CVS
                    continue;
                }
                archive(f,name,zos);
            } else {
                if(!dir.getName().equals("CVS"))
                    // we only need to archive CVS control files, not the actual workspace files
                    continue;
                zos.putNextEntry(new ZipEntry(name));
                FileInputStream fis = new FileInputStream(f);
                Util.copyStream(fis,zos);
                fis.close();
                zos.closeEntry();
            }
        }
    }

    /**
     * Parses the CVS/Entries file and adds file/directory names to the list.
     */
    private void parseCVSEntries(File entries, Set<String> knownFiles) throws IOException {
        if(!entries.exists())
            return;

        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(entries)));
        String line;
        while((line=in.readLine())!=null) {
            String[] tokens = line.split("/+");
            if(tokens==null || tokens.length<2)    continue;   // invalid format
            knownFiles.add(tokens[1]);
        }
        in.close();
    }

    /**
     * Updates the workspace as well as locate changes.
     *
     * @return
     *      List of affected file names, relative to the workspace directory.
     *      Null if the operation failed.
     */
    private List<String> update(boolean dryRun, Launcher launcher, FilePath workspace, TaskListener listener, Date date) throws IOException, InterruptedException {

        List<String> changedFileNames = new ArrayList<String>();    // file names relative to the workspace

        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add("cvs","-q","-z9");
        if(dryRun)
            cmd.add("-n");
        cmd.add("update","-PdC");
        if (branch != null) {
            cmd.add("-r", branch);
        }
        configureDate(cmd, date);

        if(flatten) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            if(!run(launcher,cmd,listener,workspace,
                new ForkOutputStream(baos,listener.getLogger())))
                return null;

            parseUpdateOutput("",baos, changedFileNames);
        } else {
            @SuppressWarnings("unchecked") // StringTokenizer oddly has the wrong type
            final Set<String> moduleNames = new TreeSet(Collections.list(new StringTokenizer(module)));

            // Add in any existing CVS dirs, in case project checked out its own.
            moduleNames.addAll(workspace.act(new FileCallable<Set<String>>() {
                public Set<String> invoke(File ws, VirtualChannel channel) throws IOException {
                    File[] subdirs = ws.listFiles();
                    if (subdirs != null) {
                        SUBDIR: for (File s : subdirs) {
                            if (new File(s, "CVS").isDirectory()) {
                                String top = s.getName();
                                for (String mod : moduleNames) {
                                    if (mod.startsWith(top + "/")) {
                                        // #190: user asked to check out foo/bar foo/baz quux
                                        // Our top-level dirs are "foo" and "quux".
                                        // Do not add "foo" to checkout or we will check out foo/*!
                                        continue SUBDIR;
                                    }
                                }
                                moduleNames.add(top);
                            }
                        }
                    }
                    return moduleNames;
                }
            }));

            for (String moduleName : moduleNames) {
                // capture the output during update
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                FilePath modulePath = new FilePath(workspace, moduleName);

                ArgumentListBuilder actualCmd = cmd;
                String baseName = moduleName;

                if(!modulePath.isDirectory()) {
                    // updating just one file, like "foo/bar.txt".
                    // run update command from "foo" directory with "bar.txt" as the command line argument
                    actualCmd = cmd.clone();
                    actualCmd.add(modulePath.getName());
                    modulePath = modulePath.getParent();
                    baseName = baseName.substring(0,baseName.lastIndexOf('/'));
                }

                if(!run(launcher,actualCmd,listener,
                    modulePath,
                    new ForkOutputStream(baos,listener.getLogger())))
                    return null;

                // we'll run one "cvs log" command with workspace as the base,
                // so use path names that are relative to moduleName.
                parseUpdateOutput(baseName+'/',baos, changedFileNames);
            }
        }

        return changedFileNames;
    }

    // see http://www.network-theory.co.uk/docs/cvsmanual/cvs_153.html for the output format.
    // we don't care '?' because that's not in the repository
    private static final Pattern UPDATE_LINE = Pattern.compile("[UPARMC] (.+)");

    private static final Pattern REMOVAL_LINE = Pattern.compile("cvs (server|update): `?(.+?)'? is no longer in the repository");
    //private static final Pattern NEWDIRECTORY_LINE = Pattern.compile("cvs server: New directory `(.+)' -- ignored");

    /**
     * Parses the output from CVS update and list up files that might have been changed.
     *
     * @param result
     *      list of file names whose changelog should be checked. This may include files
     *      that are no longer present. The path names are relative to the workspace,
     *      hence "String", not {@link File}.
     */
    private void parseUpdateOutput(String baseName, ByteArrayOutputStream output, List<String> result) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(
            new ByteArrayInputStream(output.toByteArray())));
        String line;
        while((line=in.readLine())!=null) {
            Matcher matcher = UPDATE_LINE.matcher(line);
            if(matcher.matches()) {
                result.add(baseName+matcher.group(1));
                continue;
            }

            matcher= REMOVAL_LINE.matcher(line);
            if(matcher.matches()) {
                result.add(baseName+matcher.group(2));
                continue;
            }

            // this line is added in an attempt to capture newly created directories in the repository,
            // but it turns out that this line always hit if the workspace is missing a directory
            // that the server has, even if that directory contains nothing in it
            //matcher= NEWDIRECTORY_LINE.matcher(line);
            //if(matcher.matches()) {
            //    result.add(baseName+matcher.group(1));
            //}
        }
    }

    /**
     * Returns true if we can use "cvs update" instead of "cvs checkout"
     */
    private boolean isUpdatable(FilePath dir) throws IOException, InterruptedException {
        return dir.act(new FileCallable<Boolean>() {
            public Boolean invoke(File dir, VirtualChannel channel) throws IOException {
                if(flatten) {
                    return isUpdatableModule(dir);
                } else {
                    StringTokenizer tokens = new StringTokenizer(module);
                    while(tokens.hasMoreTokens()) {
                        File module = new File(dir,tokens.nextToken());
                        if(!isUpdatableModule(module))
                            return false;
                    }
                    return true;
                }
            }
        });
    }

    private boolean isUpdatableModule(File module) {
        if(!module.isDirectory())
            // module is a file, like "foo/bar.txt". Then CVS information is "foo/CVS".
            module = module.getParentFile();

        File cvs = new File(module,"CVS");
        if(!cvs.exists())
            return false;

        // check cvsroot
        if(!checkContents(new File(cvs,"Root"),cvsroot))
            return false;
        if(branch!=null) {
            if(!checkContents(new File(cvs,"Tag"),'T'+branch))
                return false;
        } else {
            File tag = new File(cvs,"Tag");
            if (tag.exists()) {
                try {
                    Reader r = new FileReader(tag);
                    try {
                        String s = new BufferedReader(r).readLine();
                        return s != null && s.startsWith("D");
                    } finally {
                        r.close();
                    }
                } catch (IOException e) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns true if the contents of the file is equal to the given string.
     *
     * @return false in all the other cases.
     */
    private boolean checkContents(File file, String contents) {
        try {
            Reader r = new FileReader(file);
            try {
                String s = new BufferedReader(r).readLine();
                if (s == null) return false;
                return s.trim().equals(contents.trim());
            } finally {
                r.close();
            }
        } catch (IOException e) {
            return false;
        }
    }


    /**
     * Used to communicate the result of the detection in {@link CVSSCM#calcChangeLog(AbstractBuild, List, File, BuildListener)}
     */
    class ChangeLogResult implements Serializable {
        boolean hadError;
        String errorOutput;

        public ChangeLogResult(boolean hadError, String errorOutput) {
            this.hadError = hadError;
            if(hadError)
                this.errorOutput = errorOutput;
        }
        private static final long serialVersionUID = 1L;
    }

    /**
     * Used to propagate {@link BuildException} and error log at the same time.
     */
    class BuildExceptionWithLog extends RuntimeException {
        final String errorOutput;

        public BuildExceptionWithLog(BuildException cause, String errorOutput) {
            super(cause);
            this.errorOutput = errorOutput;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Computes the changelog into an XML file.
     *
     * <p>
     * When we update the workspace, we'll compute the changelog by using its output to
     * make it faster. In general case, we'll fall back to the slower approach where
     * we check all files in the workspace.
     *
     * @param changedFiles
     *      Files whose changelog should be checked for updates.
     *      This is provided if the previous operation is update, otherwise null,
     *      which means we have to fall back to the default slow computation.
     */
    private boolean calcChangeLog(AbstractBuild build, final List<String> changedFiles, File changelogFile, final BuildListener listener) throws InterruptedException {
        if(build.getPreviousBuild()==null || (changedFiles!=null && changedFiles.isEmpty())) {
            // nothing to compare against, or no changes
            // (note that changedFiles==null means fallback, so we have to run cvs log.
            listener.getLogger().println("$ no changes detected");
            return createEmptyChangeLog(changelogFile,listener, "changelog");
        }

        listener.getLogger().println("$ computing changelog");

        FilePath baseDir = build.getProject().getWorkspace();
       final String cvspassFile = getDescriptor().getCvspassFile();

        try {
            // range of time for detecting changes
            final Date startTime = build.getPreviousBuild().getTimestamp().getTime();
            final Date endTime = build.getTimestamp().getTime();
            final OutputStream out = new RemoteOutputStream(new FileOutputStream(changelogFile));

            ChangeLogResult result = baseDir.act(new FileCallable<ChangeLogResult>() {
                public ChangeLogResult invoke(File ws, VirtualChannel channel) throws IOException {
                    final StringWriter errorOutput = new StringWriter();
                    final boolean[] hadError = new boolean[1];

                    ChangeLogTask task = new ChangeLogTask() {
                        public void log(String msg, int msgLevel) {
                            // send error to listener. This seems like the route in which the changelog task
                            // sends output
                            if(msgLevel==org.apache.tools.ant.Project.MSG_ERR) {
                                hadError[0] = true;
                                errorOutput.write(msg);
                                errorOutput.write('\n');
                                return;
                            }
                            if(debugLogging) {
                                listener.getLogger().println(msg);
                            }
                        }
                    };
                    task.setProject(new org.apache.tools.ant.Project());
                    task.setDir(ws);
                    if(cvspassFile.length()!=0)
                        task.setPassfile(new File(cvspassFile));
                    task.setCvsRoot(cvsroot);
                    task.setCvsRsh(cvsRsh);
                    task.setFailOnError(true);
                    task.setDeststream(new BufferedOutputStream(out));
                    task.setBranch(branch);
                    task.setStart(startTime);
                    task.setEnd(endTime);
                    if(changedFiles!=null) {
                        // if the directory doesn't exist, cvs changelog will die, so filter them out.
                        // this means we'll lose the log of those changes
                        for (String filePath : changedFiles) {
                            if(new File(ws,filePath).getParentFile().exists())
                                task.addFile(filePath);
                        }
                    } else {
                        // fallback
                        if(!flatten)
                            task.setPackage(module);
                    }

                    try {
                        task.execute();
                    } catch (BuildException e) {
                        throw new BuildExceptionWithLog(e,errorOutput.toString());
                    }

                    return new ChangeLogResult(hadError[0],errorOutput.toString());
                }
            });

            if(result.hadError) {
                // non-fatal error must have occurred, such as cvs changelog parsing error.s
                listener.getLogger().print(result.errorOutput);
            }
            return true;
        } catch( BuildExceptionWithLog e ) {
            // capture output from the task for diagnosis
            listener.getLogger().print(e.errorOutput);
            // then report an error
            BuildException x = (BuildException) e.getCause();
            PrintWriter w = listener.error(x.getMessage());
            w.println("Working directory is "+baseDir);
            x.printStackTrace(w);
            return false;
        } catch( RuntimeException e ) {
            // an user reported a NPE inside the changeLog task.
            // we don't want a bug in Ant to prevent a build.
            e.printStackTrace(listener.error(e.getMessage()));
            return true;    // so record the message but continue
        } catch( IOException e ) {
            e.printStackTrace(listener.error("Failed to detect changlog"));
            return true;
        }
    }

    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public void buildEnvVars(Map<String,String> env) {
        if(cvsRsh!=null)
            env.put("CVS_RSH",cvsRsh);
        String cvspass = getDescriptor().getCvspassFile();
        if(cvspass.length()!=0)
            env.put("CVS_PASSFILE",cvspass);
    }

    public static final class DescriptorImpl extends Descriptor<SCM> implements ModelObject {
        static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        /**
         * Path to <tt>.cvspass</tt>. Null to default.
         */
        private String cvsPassFile;

        /**
         * Copy-on-write.
         */
        private volatile Map<String,RepositoryBrowser> browsers = new HashMap<String,RepositoryBrowser>();

        class RepositoryBrowser {
            String diffURL;
            String browseURL;
        }

        DescriptorImpl() {
            super(CVSSCM.class);
            load();
        }

        protected void convert(Map<String, Object> oldPropertyBag) {
            cvsPassFile = (String)oldPropertyBag.get("cvspass");
        }

        public String getDisplayName() {
            return "CVS";
        }

        public SCM newInstance(StaplerRequest req) {
            return new CVSSCM(
                req.getParameter("cvs_root"),
                req.getParameter("cvs_module"),
                req.getParameter("cvs_branch"),
                req.getParameter("cvs_rsh"),
                req.getParameter("cvs_use_update")!=null,
                req.getParameter("cvs_legacy")==null
            );
        }

        public String getCvspassFile() {
            String value = cvsPassFile;
            if(value==null)
                value = "";
            return value;
        }

        public void setCvspassFile(String value) {
            cvsPassFile = value;
            save();
        }

        /**
         * Gets the URL that shows the diff.
         */
        public String getDiffURL(String cvsRoot, String pathName, String oldRev, String newRev) {
            RepositoryBrowser b = browsers.get(cvsRoot);
            if(b==null)   return null;
            return b.diffURL.replaceAll("%%P",pathName).replace("%%r",oldRev).replace("%%R",newRev);

        }

        public boolean configure( StaplerRequest req ) {
            setCvspassFile(req.getParameter("cvs_cvspass"));

            Map<String,RepositoryBrowser> browsers = new HashMap<String, RepositoryBrowser>();
            int i=0;
            while(true) {
                String root = req.getParameter("cvs_repobrowser_cvsroot" + i);
                if(root==null)  break;

                RepositoryBrowser rb = new RepositoryBrowser();
                rb.browseURL = req.getParameter("cvs_repobrowser"+i);
                rb.diffURL = req.getParameter("cvs_repobrowser_diff"+i);
                browsers.put(root,rb);

                i++;
            }
            this.browsers = browsers;

            save();

            return true;
        }

    //
    // web methods
    //

        public void doCvsPassCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            // this method can be used to check if a file exists anywhere in the file system,
            // so it should be protected.
            new FormFieldValidator(req,rsp,true) {
                protected void check() throws IOException, ServletException {
                    String v = fixEmpty(request.getParameter("value"));
                    if(v==null) {
                        // default.
                        ok();
                    } else {
                        File cvsPassFile = new File(v);

                        if(cvsPassFile.exists()) {
                            ok();
                        } else {
                            error("No such file exists");
                        }
                    }
                }
            }.process();
        }

        /**
         * Displays "cvs --version" for trouble shooting.
         */
        public void doVersion(StaplerRequest req, StaplerResponse rsp) throws IOException {
            rsp.setContentType("text/plain");
            Proc proc = Hudson.getInstance().createLauncher(TaskListener.NULL).launch(
                new String[]{"cvs", "--version"}, new String[0], rsp.getOutputStream(), null);
            proc.join();
        }

        /**
         * Checks the entry to the CVSROOT field.
         * <p>
         * Also checks if .cvspass file contains the entry for this.
         */
        public void doCvsrootCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator(req,rsp,false) {
                protected void check() throws IOException, ServletException {
                    String v = fixEmpty(request.getParameter("value"));
                    if(v==null) {
                        error("CVSROOT is mandatory");
                        return;
                    }

                    // CVSROOT format isn't really that well defined. So it's hard to check this rigorously.
                    if(v.startsWith(":pserver") || v.startsWith(":ext")) {
                        if(!CVSROOT_PSERVER_PATTERN.matcher(v).matches()) {
                            error("Invalid CVSROOT string");
                            return;
                        }
                        // I can't really test if the machine name exists, either.
                        // some cvs, such as SOCKS-enabled cvs can resolve host names that Hudson might not
                        // be able to. If :ext is used, all bets are off anyway.
                    }

                    // check .cvspass file to see if it has entry.
                    // CVS handles authentication only if it's pserver.
                    if(v.startsWith(":pserver")) {
                        String cvspass = getCvspassFile();
                        File passfile;
                        if(cvspass.equals("")) {
                            passfile = new File(new File(System.getProperty("user.home")),".cvspass");
                        } else {
                            passfile = new File(cvspass);
                        }

                        if(passfile.exists()) {
                            // It's possible that we failed to locate the correct .cvspass file location,
                            // so don't report an error if we couldn't locate this file.
                            //
                            // if this is explicitly specified, then our system config page should have
                            // reported an error.
                            if(!scanCvsPassFile(passfile, v)) {
                                error("It doesn't look like this CVSROOT has its password set." +
                                    " Would you like to set it now?");
                                return;
                            }
                        }
                    }

                    // all tests passed so far
                    ok();
                }
            }.process();
        }

        /**
         * Checks if the given pserver CVSROOT value exists in the pass file.
         */
        private boolean scanCvsPassFile(File passfile, String cvsroot) throws IOException {
            cvsroot += ' ';
            String cvsroot2 = "/1 "+cvsroot; // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5006835
            BufferedReader in = new BufferedReader(new FileReader(passfile));
            try {
                String line;
                while((line=in.readLine())!=null) {
                    // "/1 " version always have the port number in it, so examine a much with
                    // default port 2401 left out
                    int portIndex = line.indexOf(":2401/");
                    String line2 = "";
                    if(portIndex>=0)
                        line2 = line.substring(0,portIndex+1)+line.substring(portIndex+5); // leave '/'

                    if(line.startsWith(cvsroot) || line.startsWith(cvsroot2) || line2.startsWith(cvsroot2))
                        return true;
                }
                return false;
            } finally {
                in.close();
            }
        }

        private static final Pattern CVSROOT_PSERVER_PATTERN =
            Pattern.compile(":(ext|pserver):[^@:]+@[^:]+:(\\d+:)?.+");

        /**
         * Runs cvs login command.
         *
         * TODO: this apparently doesn't work. Probably related to the fact that
         * cvs does some tty magic to disable echo back or whatever.
         */
        public void doPostPassword(StaplerRequest req, StaplerResponse rsp) throws IOException {
            if(!Hudson.adminCheck(req,rsp))
                return;

            String cvsroot = req.getParameter("cvsroot");
            String password = req.getParameter("password");

            if(cvsroot==null || password==null) {
                rsp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            rsp.setContentType("text/plain");
            Proc proc = Hudson.getInstance().createLauncher(TaskListener.NULL).launch(
                new String[]{"cvs", "-d",cvsroot,"login"}, new String[0],
                new ByteArrayInputStream((password+"\n").getBytes()),
                rsp.getOutputStream());
            proc.join();
        }
    }

    /**
     * Action for a build that performs the tagging.
     */
    public final class TagAction implements Action {
        private final AbstractBuild build;

        /**
         * If non-null, that means the build is already tagged.
         */
        private String tagName;

        /**
         * If non-null, that means the tagging is in progress
         * (asynchronously.)
         */
        private transient TagWorkerThread workerThread;

        public TagAction(AbstractBuild build) {
            this.build = build;
        }

        public String getIconFileName() {
            return "save.gif";
        }

        public String getDisplayName() {
            return "Tag this build";
        }

        public String getUrlName() {
            return "tagBuild";
        }

        public String getTagName() {
            return tagName;
        }

        public TagWorkerThread getWorkerThread() {
            return workerThread;
        }

        public AbstractBuild getBuild() {
            return build;
        }

        public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            req.setAttribute("build",build);
            req.getView(this,chooseAction()).forward(req,rsp);
        }

        private synchronized String chooseAction() {
            if(tagName!=null)
                return "alreadyTagged.jelly";
            if(workerThread!=null)
                return "inProgress.jelly";
            return "tagForm.jelly";
        }

        /**
         * Invoked to actually tag the workspace.
         */
        public synchronized void doSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            String name = req.getParameter("name");
            if(name==null || name.length()==0) {
                // invalid tag name
                doIndex(req,rsp);
                return;
            }

            if(workerThread==null) {
                workerThread = new TagWorkerThread(name);
                workerThread.start();
            }

            doIndex(req,rsp);
        }

        /**
         * Clears the error status.
         */
        public synchronized void doClearError(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            if(workerThread!=null && !workerThread.isAlive())
                workerThread = null;
            doIndex(req,rsp);
        }

        public final class TagWorkerThread extends Thread {
            private final String tagName;
            // StringWriter is synchronized
            private final ByteArrayOutputStream log = new ByteArrayOutputStream();

            public TagWorkerThread(String tagName) {
                this.tagName = tagName;
            }

            public String getLog() {
                // this method can be invoked from another thread.
                return log.toString();
            }

            public String getTagName() {
                return tagName;
            }

            public void run() {
                BuildListener listener = new StreamBuildListener(log);

                Result result = Result.FAILURE;
                File destdir = null;
                listener.started();
                try {
                    destdir = Util.createTempDir();

                    // unzip the archive
                    listener.getLogger().println("expanding the workspace archive into "+destdir);
                    Expand e = new Expand();
                    e.setProject(new org.apache.tools.ant.Project());
                    e.setDest(destdir);
                    e.setSrc(getArchiveFile(build));
                    e.setTaskType("unzip");
                    e.execute();

                    // run cvs tag command
                    listener.getLogger().println("tagging the workspace");
                    StringTokenizer tokens = new StringTokenizer(CVSSCM.this.module);
                    while(tokens.hasMoreTokens()) {
                        String m = tokens.nextToken();
                        FilePath path = new FilePath(destdir).child(m);
                        boolean isDir = path.isDirectory();

                        ArgumentListBuilder cmd = new ArgumentListBuilder();
                        cmd.add("cvs","tag");
                        if(isDir) {
                            cmd.add("-R");
                        }
                        cmd.add(tagName);
                        if(!isDir) {
                            cmd.add(path.getName());
                            path = path.getParent();
                        }

                        if(!CVSSCM.this.run(new Launcher.LocalLauncher(listener),cmd,listener, path)) {
                            listener.getLogger().println("tagging failed");
                            return;
                        }
                    }

                    // completed successfully
                    synchronized(TagAction.this) {
                        TagAction.this.tagName = this.tagName;
                        TagAction.this.workerThread = null;
                    }
                    build.save();

                } catch (Throwable e) {
                    e.printStackTrace(listener.fatalError(e.getMessage()));
                } finally {
                    try {
                        if(destdir!=null) {
                            listener.getLogger().println("cleaning up "+destdir);
                            Util.deleteRecursive(destdir);
                        }
                    } catch (IOException e) {
                        e.printStackTrace(listener.fatalError(e.getMessage()));
                    }
                    listener.finished(result);
                }
            }
        }
    }

    /**
     * Temporary hack for assisting trouble-shooting.
     *
     * <p>
     * Setting this property to true would cause <tt>cvs log</tt> to dump a lot of messages.
     */
    public static boolean debugLogging = false;

    private static final long serialVersionUID = 1L;
}
