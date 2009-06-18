/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jene Jasper, Stephen Connolly
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
package hudson.scm;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.Extension;
import static hudson.Util.fixEmpty;
import static hudson.Util.fixNull;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TaskThread;
import hudson.org.apache.tools.ant.taskdefs.cvslib.ChangeLogTask;
import hudson.remoting.Future;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.util.ArgumentListBuilder;
import hudson.util.ForkOutputStream;
import hudson.util.IOException2;
import hudson.util.FormValidation;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Expand;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.framework.io.ByteBuffer;

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
import java.io.Serializable;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.sf.json.JSONObject;

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
public class CVSSCM extends SCM implements Serializable {
    /**
     * CVSSCM connection string, like ":pserver:me@host:/cvs"
     */
    private String cvsroot;

    /**
     * Module names.
     *
     * This could be a whitespace/NL-separated list of multiple modules.
     * Modules could be either directories or files. "\ " is used to escape
     * " ", which is needed for modules with whitespace in it.
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

    private CVSRepositoryBrowser repositoryBrowser;

    private boolean isTag;

	 private String excludedRegions;

    @DataBoundConstructor
    public CVSSCM(String cvsRoot, String module,String branch,String cvsRsh,boolean canUseUpdate, boolean legacy, boolean isTag, String excludedRegions) {
        if(fixNull(branch).equals("HEAD"))
            branch = null;

        this.cvsroot = cvsRoot;
        this.module = module.trim();
        this.branch = nullify(branch);
        this.cvsRsh = nullify(cvsRsh);
        this.canUseUpdate = canUseUpdate;
        this.flatten = !legacy && getAllModulesNormalized().length==1;
        this.isTag = isTag;
	    this.excludedRegions = excludedRegions;
    }

    @Override
    public CVSRepositoryBrowser getBrowser() {
        return repositoryBrowser;
    }

    private String compression() {
        if(getDescriptor().isNoCompression())
            return null;

        // CVS 1.11.22 manual:
        // If the access method is omitted, then if the repository starts with
        // `/', then `:local:' is assumed.  If it does not start with `/' then
        // either `:ext:' or `:server:' is assumed.
        boolean local = cvsroot.startsWith("/") || cvsroot.startsWith(":local:") || cvsroot.startsWith(":fork:");
        // For local access, compression is senseless. For remote, use z3:
        // http://root.cern.ch/root/CVS.html#checkout
        return local ? "-z0" : "-z3";
    }

    public String getCvsRoot() {
        return cvsroot;
    }

    /**
     * Returns true if {@link #getBranch()} represents a tag.
     * <p>
     * This causes Hudson to stop using "-D" option while check out and update.
     */
    public boolean isTag() {
        return isTag;
    }

    /**
     * If there are multiple modules, return the module directory of the first one.
     * @param workspace
     */
    public FilePath getModuleRoot(FilePath workspace) {
        if(flatten)
            return workspace;

        return workspace.child(getAllModulesNormalized()[0]);
    }

    public FilePath[] getModuleRoots(FilePath workspace) {
        if (!flatten) {
            final String[] moduleLocations = getAllModulesNormalized();
            FilePath[] moduleRoots = new FilePath[moduleLocations.length];
            for (int i = 0; i < moduleLocations.length; i++) {
                moduleRoots[i] = workspace.child(moduleLocations[i]);
            }
            return moduleRoots;
        }
        return new FilePath[]{getModuleRoot(workspace)};
    }

    public ChangeLogParser createChangeLogParser() {
        return new CVSChangeLogParser();
    }

    public String getAllModules() {
        return module;
    }

	 public String getExcludedRegions() {
		  return excludedRegions;
	 }

	 public String[] getExcludedRegionsNormalized() {
		  return excludedRegions == null ? null : excludedRegions.split("[\\r\\n]+");
	 }

	 private Pattern[] getExcludedRegionsPatterns() {
		 String[] excludedRegions = getExcludedRegionsNormalized();
		 if (excludedRegions != null)
		 {
			 Pattern[] patterns = new Pattern[excludedRegions.length];

			 int i = 0;
			 for (String excludedRegion : excludedRegions)
			 {
				 patterns[i++] = Pattern.compile(excludedRegion);
			 }

			 return patterns;
		 }

		 return null;
	 }

    /**
     * List up all modules to check out.
     */
    public String[] getAllModulesNormalized() {
        // split by whitespace, except "\ "
        String[] r = module.split("(?<!\\\\)[ \\r\\n]+");
        // now replace "\ " to " ".
        for (int i = 0; i < r.length; i++)
            r[i] = r[i].replaceAll("\\\\ "," ");
        return r;
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

    public boolean isLegacy() {
        return !flatten;
    }

    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath dir, TaskListener listener) throws IOException, InterruptedException {
        String why = isUpdatable(dir);
        if(why!=null) {
            listener.getLogger().println(Messages.CVSSCM_WorkspaceInconsistent(why));
            return true;
        }

        List<String> changedFiles = update(true, launcher, dir, listener, new Date());

	     if (changedFiles != null && !changedFiles.isEmpty())
	     {
		     Pattern[] patterns = getExcludedRegionsPatterns();

		     if (patterns != null)
		     {
			     boolean areThereChanges = false;

			     for (String changedFile : changedFiles)
			     {
				     boolean patternMatched = false;

				     for (Pattern pattern : patterns)
				     {
					     if (pattern.matcher(changedFile).matches())
					     {
						     patternMatched = true;
						     break;
					     }
				     }

				     if (!patternMatched)
				     {
					     areThereChanges = true;
					     break;
				     }
			     }

			     return areThereChanges;
		     }

		     // no excluded patterns so just return true as
		     // changedFiles != null && !changedFiles.isEmpty() is true
		     return true;
	     }

	     return false;
    }

    private void configureDate(ArgumentListBuilder cmd, Date date) { // #192
        if(isTag)   return; // don't use the -D option.
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC")); // #209
        cmd.add("-D", df.format(date));
    }

    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath ws, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        List<String> changedFiles = null; // files that were affected by update. null this is a check out

        if(canUseUpdate && isUpdatable(ws)==null) {
            changedFiles = update(false, launcher, ws, listener, build.getTimestamp().getTime());
            if(changedFiles==null)
                return false;   // failed
        } else {
            if(!checkout(launcher,ws,listener,build.getTimestamp().getTime()))
                return false;
        }

        // archive the workspace to support later tagging
        File archiveFile = getArchiveFile(build);
        final OutputStream os = new RemoteOutputStream(new FileOutputStream(archiveFile));

        ws.act(new FileCallable<Void>() {
            public Void invoke(File ws, VirtualChannel channel) throws IOException {
                ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os));

                if(flatten) {
                    archive(ws, module, zos,true);
                } else {
                    for (String m : getAllModulesNormalized()) {
                        File mf = new File(ws, m);

                        if(!mf.exists())
                            // directory doesn't exist. This happens if a directory that was checked out
                            // didn't include any file.
                            continue;

                        if(!mf.isDirectory()) {
                            // this module is just a file, say "foo/bar.txt".
                            // to record "foo/CVS/*", we need to start by archiving "foo".
                            int idx = m.lastIndexOf('/');
                            if(idx==-1)
                                throw new Error("Kohsuke probe: m="+m);
                            m = m.substring(0, idx);
                            mf = mf.getParentFile();
                        }
                        archive(mf,m,zos,true);
                    }
                }
                zos.close();
                return null;
            }
        });

        // contribute the tag action
        build.getActions().add(new TagAction(build));

        return calcChangeLog(build, ws, changedFiles, changelogFile, listener);
    }

    public boolean checkout(Launcher launcher, FilePath dir, TaskListener listener) throws IOException, InterruptedException {
        Date now = new Date();
        if(canUseUpdate && isUpdatable(dir)==null) {
            return update(false, launcher, dir, listener, now)!=null;
        } else {
            return checkout(launcher,dir,listener, now);
        }
    }

    private boolean checkout(Launcher launcher, FilePath dir, TaskListener listener, Date dt) throws IOException, InterruptedException {
        dir.deleteContents();

        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(getDescriptor().getCvsExeOrDefault(), noQuiet?null:(debug ?"-t":"-Q"),compression(),"-d",cvsroot,"co","-P");
        if(branch!=null)
            cmd.add("-r",branch);
        if(flatten)
            cmd.add("-d",dir.getName());
        configureDate(cmd,dt);
        cmd.add(getAllModulesNormalized());

        if(!run(launcher,cmd,listener, flatten ? dir.getParent() : dir))
            return false;

        // clean up the sticky tag
        if(flatten)
            dir.act(new StickyDateCleanUpTask());
        else {
            for (String module : getAllModulesNormalized()) {
                dir.child(module).act(new StickyDateCleanUpTask());
            }
        }
        return true;
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
    private void archive(File dir,String relPath,ZipOutputStream zos, boolean isRoot) throws IOException {
        Set<String> knownFiles = new HashSet<String>();
        // see http://www.monkey.org/openbsd/archive/misc/9607/msg00056.html for what Entries.Log is for
        parseCVSEntries(new File(dir,"CVS/Entries"),knownFiles);
        parseCVSEntries(new File(dir,"CVS/Entries.Log"),knownFiles);
        parseCVSEntries(new File(dir,"CVS/Entries.Extra"),knownFiles);
        boolean hasCVSdirs = !knownFiles.isEmpty();
        knownFiles.add("CVS");

        File[] files = dir.listFiles();
        if(files==null) {
            if(isRoot)
                throw new IOException("No such directory exists. Did you specify the correct branch? Perhaps you specified a tag: "+dir);
            else
                throw new IOException("No such directory exists. Looks like someone is modifying the workspace concurrently: "+dir);
        }
        for( File f : files ) {
            String name = relPath+'/'+f.getName();
            if(f.isDirectory()) {
                if(hasCVSdirs && !knownFiles.contains(f.getName())) {
                    // not controlled in CVS. Skip.
                    // but also make sure that we archive CVS/*, which doesn't have CVS/CVS
                    continue;
                }
                archive(f,name,zos,false);
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
        cmd.add(getDescriptor().getCvsExeOrDefault(),"-q",compression());
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

            // asynchronously start cleaning up the sticky tag while we work on parsing the result
            Future<Void> task = workspace.actAsync(new StickyDateCleanUpTask());
            parseUpdateOutput("",baos, changedFileNames);
            join(task);
        } else {
            @SuppressWarnings("unchecked") // StringTokenizer oddly has the wrong type
            final Set<String> moduleNames = new TreeSet(Arrays.asList(getAllModulesNormalized()));

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
                    int slash = baseName.lastIndexOf('/');
                    if (slash > 0) {
                        baseName = baseName.substring(0, slash);
                    }
                }

                if(!run(launcher,actualCmd,listener,
                    modulePath,
                    new ForkOutputStream(baos,listener.getLogger())))
                    return null;

                // asynchronously start cleaning up the sticky tag while we work on parsing the result
                Future<Void> task = modulePath.actAsync(new StickyDateCleanUpTask());

                // we'll run one "cvs log" command with workspace as the base,
                // so use path names that are relative to moduleName.
                parseUpdateOutput(baseName+'/',baos, changedFileNames);

                join(task);
            }
        }

        return changedFileNames;
    }

    private void join(Future<Void> task) throws InterruptedException, IOException {
        try {
            task.get();
        } catch (ExecutionException e) {
            throw new IOException2(e);
        }
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
     * Returns null if we can use "cvs update" instead of "cvs checkout"
     *
     * @return
     *      If update is impossible, return the text explaining why.
     */
    private String isUpdatable(FilePath dir) throws IOException, InterruptedException {
        return dir.act(new FileCallable<String>() {
            public String invoke(File dir, VirtualChannel channel) throws IOException {
                if(flatten) {
                    return isUpdatableModule(dir);
                } else {
                    for (String m : getAllModulesNormalized()) {
                        File module = new File(dir,m);
                        String reason = isUpdatableModule(module);
                        if(reason!=null)
                            return reason;
                    }
                    return null;
                }
            }

            private String isUpdatableModule(File module) {
                try {
                    if(!module.isDirectory())
                        // module is a file, like "foo/bar.txt". Then CVS information is "foo/CVS".
                        module = module.getParentFile();

                    File cvs = new File(module,"CVS");
                    if(!cvs.exists())
                        return "No CVS dir in "+module;

                    // check cvsroot
                    File cvsRootFile = new File(cvs, "Root");
                    if(!checkContents(cvsRootFile,cvsroot))
                        return cvs+"/Root content mismatch: expected "+cvsroot+" but found "+FileUtils.readFileToString(cvsRootFile);
                    if(branch!=null) {
                        if(!checkContents(new File(cvs,"Tag"),(isTag()?'N':'T')+branch))
                            return cvs+" branch mismatch";
                    } else {
                        File tag = new File(cvs,"Tag");
                        if (tag.exists()) {
                            BufferedReader r = new BufferedReader(new FileReader(tag));
                            try {
                                String s = r.readLine();
                                if(s != null && s.startsWith("D"))  return null;    // OK
                                return "Workspace is on branch "+s;
                            } finally {
                                r.close();
                            }
                        }
                    }

                    return null;
                } catch (IOException e) {
                    return e.getMessage();
                }
            }
        });
    }

    /**
     * Returns true if the contents of the file is equal to the given string.
     *
     * @return false in all the other cases.
     */
    private boolean checkContents(File file, String contents) {
        try {
            BufferedReader r = new BufferedReader(new FileReader(file));
            try {
                String s = r.readLine();
                if (s == null) return false;
                return massageForCheckContents(s).equals(massageForCheckContents(contents));
            } finally {
                r.close();
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Normalize the string for comparison in {@link #checkContents(File, String)}.
     */
    private String massageForCheckContents(String s) {
        s=s.trim();
        // this is somewhat ugly because we only want to do this for CVS/Root but still ended up doing this
        // for all checks. OTOH, there shouldn'be really any false positive.
        Matcher m = PSERVER_CVSROOT_WITH_PASSWORD.matcher(s);
        if(m.matches())
            s = m.group(1)+m.group(2);  // cut off password
        return s;
    }

    /**
     * Looks for CVSROOT that includes password, like ":pserver:uid:pwd@server:/path".
     *
     * <p>
     * Some CVS client (likely CVSNT?) appears to add the password despite the fact that CVSROOT Hudson is setting
     * doesn't include one. So when we compare CVSROOT, we need to remove the password.
     *
     * <p>
     * Since the password equivalence shouldn't really affect the {@link #checkContents(File, String)}, we use
     * this pattern to ignore password from both {@link #cvsroot} and the string found in <tt>path/CVS/Root</tt>
     * and then compare.
     *
     * See http://www.nabble.com/Problem-with-polling-CVS%2C-from-version-1.181-tt15799926.html for the user report.
     */
    private static final Pattern PSERVER_CVSROOT_WITH_PASSWORD = Pattern.compile("(:pserver:[^@:]+):[^@:]+(@.+)");

    /**
     * Used to communicate the result of the detection in {@link CVSSCM#calcChangeLog(AbstractBuild, FilePath, List, File, BuildListener)}
     */
    static class ChangeLogResult implements Serializable {
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
    static class BuildExceptionWithLog extends RuntimeException {
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
    private boolean calcChangeLog(AbstractBuild build, FilePath ws, final List<String> changedFiles, File changelogFile, final BuildListener listener) throws InterruptedException {
        if(build.getPreviousBuild()==null || (changedFiles!=null && changedFiles.isEmpty())) {
            // nothing to compare against, or no changes
            // (note that changedFiles==null means fallback, so we have to run cvs log.
            listener.getLogger().println("$ no changes detected");
            return createEmptyChangeLog(changelogFile,listener, "changelog");
        }
        if(skipChangeLog) {
            listener.getLogger().println("Skipping changelog computation");
            return createEmptyChangeLog(changelogFile,listener, "changelog");
        }

        listener.getLogger().println("$ computing changelog");

        final String cvspassFile = getDescriptor().getCvspassFile();
        final String cvsExe = getDescriptor().getCvsExeOrDefault();

        OutputStream o = null;
        try {
            // range of time for detecting changes
            final Date startTime = build.getPreviousBuild().getTimestamp().getTime();
            final Date endTime = build.getTimestamp().getTime();
            final OutputStream out = o = new RemoteOutputStream(new FileOutputStream(changelogFile));

            ChangeLogResult result = ws.act(new FileCallable<ChangeLogResult>() {
                public ChangeLogResult invoke(File ws, VirtualChannel channel) throws IOException {
                    final StringWriter errorOutput = new StringWriter();
                    final boolean[] hadError = new boolean[1];

                    ChangeLogTask task = new ChangeLogTask() {
                        public void log(String msg, int msgLevel) {
                            if(msgLevel==org.apache.tools.ant.Project.MSG_ERR)
                                hadError[0] = true;
                            // send error to listener. This seems like the route in which the changelog task
                            // sends output.
                            // Also in ChangeLogTask.getExecuteStreamHandler, we send stderr from CVS
                            // at WARN level.
                            if(msgLevel<=org.apache.tools.ant.Project.MSG_WARN) {
                                errorOutput.write(msg);
                                errorOutput.write('\n');
                                return;
                            }
                            if(debug) {
                                listener.getLogger().println(msg);
                            }
                        }
                    };
                    task.setProject(new org.apache.tools.ant.Project());
                    task.setCvsExe(cvsExe);
                    task.setDir(ws);
                    if(cvspassFile.length()!=0)
                        task.setPassfile(new File(cvspassFile));
                    if (canUseUpdate && cvsroot.startsWith("/")) {
                        // cvs log of built source trees unreliable in local access method:
                        // https://savannah.nongnu.org/bugs/index.php?15223
                        task.setCvsRoot(":fork:" + cvsroot);
                    } else if (canUseUpdate && cvsroot.startsWith(":local:")) {
                        task.setCvsRoot(":fork:" + cvsroot.substring(7));
                    } else {
                        task.setCvsRoot(cvsroot);
                    }
                    task.setCvsRsh(cvsRsh);
                    task.setFailOnError(true);
                    BufferedOutputStream bufferedOutput = new BufferedOutputStream(out);
                    task.setDeststream(bufferedOutput);
                    task.setBranch(branch);
                    task.setStart(startTime);
                    task.setEnd(endTime);
                    if(changedFiles!=null) {
                        // we can optimize the processing if we know what files have changed.
                        // but also try not to make the command line too long so as no to hit
                        // the system call limit to the command line length (see issue #389)
                        // the choice of the number is arbitrary, but normally we don't really
                        // expect continuous builds to have too many changes, so this should be OK.
                        if(changedFiles.size()<100 || !Hudson.isWindows()) {
                            // if the directory doesn't exist, cvs changelog will die, so filter them out.
                            // this means we'll lose the log of those changes
                            for (String filePath : changedFiles) {
                                if(new File(ws,filePath).getParentFile().exists())
                                    task.addFile(filePath);
                            }
                        }
                    } else {
                        // fallback
                        if(!flatten)
                            task.setPackage(getAllModulesNormalized());
                    }

                    try {
                        task.execute();
                    } catch (BuildException e) {
                        throw new BuildExceptionWithLog(e,errorOutput.toString());
                    } finally {
                        bufferedOutput.close();
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
            w.println("Working directory is "+ ws);
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
        } finally {
            IOUtils.closeQuietly(o);
        }
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public void buildEnvVars(AbstractBuild build, Map<String, String> env) {
        if(cvsRsh!=null)
            env.put("CVS_RSH",cvsRsh);
        if(branch!=null)
            env.put("CVS_BRANCH",branch);
        String cvspass = getDescriptor().getCvspassFile();
        if(cvspass.length()!=0)
            env.put("CVS_PASSFILE",cvspass);
    }

    /**
     * Invokes the command with the specified command line option and wait for its completion.
     *
     * @param dir
     *      if launching locally this is a local path, otherwise a remote path.
     * @param out
     *      Receives output from the executed program.
     */
    protected final boolean run(Launcher launcher, ArgumentListBuilder cmd, TaskListener listener, FilePath dir, OutputStream out) throws IOException, InterruptedException {
        Map<String,String> env = createEnvVarMap(true);

        int r = launcher.launch().cmds(cmd).envs(env).stdout(out).pwd(dir).join();
        if(r!=0)
            listener.fatalError(getDescriptor().getDisplayName()+" failed. exit code="+r);

        return r==0;
    }

    protected final boolean run(Launcher launcher, ArgumentListBuilder cmd, TaskListener listener, FilePath dir) throws IOException, InterruptedException {
        return run(launcher,cmd,listener,dir,listener.getLogger());
    }

    /**
     *
     * @param overrideOnly
     *      true to indicate that the returned map shall only contain
     *      properties that need to be overridden. This is for use with {@link Launcher}.
     *      false to indicate that the map should contain complete map.
     *      This is to invoke {@link Proc} directly.
     */
    protected final Map<String,String> createEnvVarMap(boolean overrideOnly) {
        Map<String,String> env = new HashMap<String,String>();
        if(!overrideOnly)
            env.putAll(EnvVars.masterEnvVars);
        buildEnvVars(null/*TODO*/,env);
        return env;
    }

    /**
     * Recursively visits directories and get rid of the sticky date in <tt>CVS/Entries</tt> folder.
     */
    private static final class StickyDateCleanUpTask implements FileCallable<Void> {
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            process(f);
            return null;
        }

        private void process(File f) throws IOException {
            File entries = new File(f,"CVS/Entries");
            if(!entries.exists())
                return; // not a CVS-controlled directory. No point in recursing

            boolean modified = false;
            String contents = FileUtils.readFileToString(entries);
            StringBuilder newContents = new StringBuilder(contents.length());
            String[] lines = contents.split("\n");
            
            for (String line : lines) {
                int idx = line.lastIndexOf('/');
                if(idx==-1) continue;       // something is seriously wrong with this line. just skip.

                String date = line.substring(idx+1);
                if(STICKY_DATE.matcher(date.trim()).matches()) {
                    // the format is like "D2008.01.21.23.30.44"
                    line = line.substring(0,idx+1);
                    modified = true;
                }

                newContents.append(line).append('\n');
            }

            if(modified) {
                // write it back
                File tmp = new File(f, "CVS/Entries.tmp");
                FileUtils.writeStringToFile(tmp,newContents.toString());
                entries.delete();
                tmp.renameTo(entries);
            }

            // recursively process children
            File[] children = f.listFiles();
            if(children!=null) {
                for (File child : children)
                    process(child);
            }
        }

        private static final Pattern STICKY_DATE = Pattern.compile("D\\d\\d\\d\\d\\.\\d\\d\\.\\d\\d\\.\\d\\d\\.\\d\\d\\.\\d\\d");
    }

    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<CVSSCM> implements ModelObject {
        /**
         * Path to <tt>.cvspass</tt>. Null to default.
         */
        private String cvsPassFile;

        /**
         * Path to cvs executable. Null to just use "cvs".
         */
        private String cvsExe;

        /**
         * Disable CVS compression support.
         */
        private boolean noCompression;

        // compatibility only
        private transient Map<String,RepositoryBrowser> browsers;

        // compatibility only
        class RepositoryBrowser {
            String diffURL;
            String browseURL;
        }

        public DescriptorImpl() {
            super(CVSRepositoryBrowser.class);
            load();
        }

        protected void convert(Map<String, Object> oldPropertyBag) {
            cvsPassFile = (String)oldPropertyBag.get("cvspass");
        }

        public String getDisplayName() {
            return "CVS";
        }

        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            CVSSCM scm = req.bindJSON(CVSSCM.class,formData);
            scm.repositoryBrowser = RepositoryBrowsers.createInstance(CVSRepositoryBrowser.class,req,formData,"browser");
            return scm;
        }

        public String getCvspassFile() {
            String value = cvsPassFile;
            if(value==null)
                value = "";
            return value;
        }

        public String getCvsExe() {
            return cvsExe;
        }

        public String getCvsExeOrDefault() {
            if(Util.fixEmpty(cvsExe)==null)     return "cvs";
            else                                return cvsExe;
        }

        public void setCvspassFile(String value) {
            cvsPassFile = value;
            save();
        }

        public boolean isNoCompression() {
            return noCompression;
        }

        public boolean configure( StaplerRequest req, JSONObject o ) {
            cvsPassFile = fixEmpty(req.getParameter("cvs_cvspass").trim());
            cvsExe = fixEmpty(req.getParameter("cvs_exe").trim());
            noCompression = req.getParameter("cvs_noCompression")!=null;
            save();

            return true;
        }

        @Override
        public boolean isBrowserReusable(CVSSCM x, CVSSCM y) {
            return x.getCvsRoot().equals(y.getCvsRoot());
        }

    //
    // web methods
    //

        public FormValidation doCvsPassCheck(@QueryParameter String value) {
            // this method can be used to check if a file exists anywhere in the file system,
            // so it should be protected.
            if(!Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
                return FormValidation.ok();

            value = fixEmpty(value);
            if(value==null) // not entered
                return FormValidation.ok();

            File cvsPassFile = new File(value);

            if(cvsPassFile.exists()) {
                if(cvsPassFile.isDirectory())
                    return FormValidation.error(cvsPassFile+" is a directory");
                else
                    return FormValidation.ok();
            }

            return FormValidation.error("No such file exists");
        }

        /**
         * Checks if cvs executable exists.
         */
        public FormValidation doCvsExeCheck(@QueryParameter String value) {
            return FormValidation.validateExecutable(value);
        }

        /**
         * Displays "cvs --version" for trouble shooting.
         */
        public void doVersion(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
            ByteBuffer baos = new ByteBuffer();
            try {
                Hudson.getInstance().createLauncher(TaskListener.NULL).launch()
                        .cmds(getCvsExeOrDefault(), "--version").stdout(baos).join();
                rsp.setContentType("text/plain");
                baos.writeTo(rsp.getOutputStream());
            } catch (IOException e) {
                req.setAttribute("error",e);
                rsp.forward(this,"versionCheckError",req);
            }
        }

        /**
         * Checks the correctness of the branch name.
         */
        public FormValidation doCheckBranch(@QueryParameter String value) {
            String v = fixNull(value);

            if(v.equals("HEAD"))
                return FormValidation.error(Messages.CVSSCM_HeadIsNotBranch());

            return FormValidation.ok();
        }

        /**
         * Checks the entry to the CVSROOT field.
         * <p>
         * Also checks if .cvspass file contains the entry for this.
         */
        public FormValidation doCheckCvsRoot(@QueryParameter String value) throws IOException {
            String v = fixEmpty(value);
            if(v==null)
                return FormValidation.error(Messages.CVSSCM_MissingCvsroot());

            Matcher m = CVSROOT_PSERVER_PATTERN.matcher(v);

            // CVSROOT format isn't really that well defined. So it's hard to check this rigorously.
            if(v.startsWith(":pserver") || v.startsWith(":ext")) {
                if(!m.matches())
                    return FormValidation.error(Messages.CVSSCM_InvalidCvsroot());
                // I can't really test if the machine name exists, either.
                // some cvs, such as SOCKS-enabled cvs can resolve host names that Hudson might not
                // be able to. If :ext is used, all bets are off anyway.
            }

            // check .cvspass file to see if it has entry.
            // CVS handles authentication only if it's pserver.
            if(v.startsWith(":pserver")) {
                if(m.group(2)==null) {// if password is not specified in CVSROOT
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
                        if(!scanCvsPassFile(passfile, v))
                            return FormValidation.error(Messages.CVSSCM_PasswordNotSet());
                    }
                }
            }
            return FormValidation.ok();
        }

	    /**
         * Validates the excludeRegions Regex
         */
        public FormValidation doCheckExcludeRegions(@QueryParameter String value) {
            String v = fixNull(value).trim();

            for (String region : v.split("[\\r\\n]+"))
                try {
                    Pattern.compile(region);
                } catch (PatternSyntaxException e) {
                    return FormValidation.error("Invalid regular expression. " + e.getMessage());
                }
            return FormValidation.ok();
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
            Pattern.compile(":(ext|extssh|pserver):[^@:]+(:[^@:]+)?@[^:]+:(\\d+:)?.+");

        /**
         * Runs cvs login command.
         *
         * TODO: this apparently doesn't work. Probably related to the fact that
         * cvs does some tty magic to disable echo back or whatever.
         */
        public void doPostPassword(StaplerRequest req, StaplerResponse rsp) throws IOException, InterruptedException {
            Hudson.getInstance().checkPermission(Hudson.ADMINISTER);

            String cvsroot = req.getParameter("cvsroot");
            String password = req.getParameter("password");

            if(cvsroot==null || password==null) {
                rsp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            rsp.setContentType("text/plain");
            Proc proc = Hudson.getInstance().createLauncher(TaskListener.NULL).launch()
                    .cmds(getCvsExeOrDefault(), "-d",cvsroot,"login")
                    .stdin(new ByteArrayInputStream((password+"\n").getBytes()))
                    .stdout(rsp.getOutputStream()).start();
            proc.join();
        }
    }

    /**
     * Action for a build that performs the tagging.
     */
    public final class TagAction extends AbstractScmTagAction {

        /**
         * If non-null, that means the build is already tagged.
         * If multiple tags are created, those are whitespace-separated.
         */
        private volatile String tagName;

        public TagAction(AbstractBuild build) {
            super(build);
        }

        public String getIconFileName() {
            if(tagName==null && !build.getParent().getACL().hasPermission(TAG))
                return null;
            return "save.gif";
        }

        public String getDisplayName() {
            if(tagName==null)
                return Messages.CVSSCM_TagThisBuild();
            if(tagName.indexOf(' ')>=0)
                return Messages.CVSSCM_DisplayName2();
            else
                return Messages.CVSSCM_DisplayName1();
        }

        public String[] getTagNames() {
            if(tagName==null)   return new String[0];
            return tagName.split(" ");
        }

        /**
         * Checks if the value is a valid CVS tag name.
         */
        public synchronized FormValidation doCheckTag(@QueryParameter String value) {
            String tag = fixNull(value).trim();
            if(tag.length()==0) // nothing entered yet
                return FormValidation.ok();
            return FormValidation.error(isInvalidTag(tag));
        }

        @Override
        public Permission getPermission() {
            return TAG;
        }

        @Override
        public String getTooltip() {
            if(tagName!=null)   return "Tag: "+tagName;
            else                return null;
        }

        @Override
        public boolean isTagged() {
            return tagName!=null;
        }

        /**
         * Invoked to actually tag the workspace.
         */
        public synchronized void doSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            build.checkPermission(getPermission());

            Map<AbstractBuild,String> tagSet = new HashMap<AbstractBuild,String>();

            String name = fixNull(req.getParameter("name")).trim();
            String reason = isInvalidTag(name);
            if(reason!=null) {
                sendError(reason,req,rsp);
                return;
            }

            tagSet.put(build,name);

            if(req.getParameter("upstream")!=null) {
                // tag all upstream builds
                Enumeration e = req.getParameterNames();
                Map<AbstractProject, Integer> upstreams = build.getUpstreamBuilds(); // TODO: define them at AbstractBuild level

                while(e.hasMoreElements()) {
                    String upName = (String) e.nextElement();
                    if(!upName.startsWith("upstream."))
                        continue;

                    String tag = fixNull(req.getParameter(upName)).trim();
                    reason = isInvalidTag(tag);
                    if(reason!=null) {
                        sendError(Messages.CVSSCM_NoValidTagNameGivenFor(upName,reason),req,rsp);
                        return;
                    }

                    upName = upName.substring(9);   // trim off 'upstream.'
                    AbstractProject p = Hudson.getInstance().getItemByFullName(upName,AbstractProject.class);
                    if(p==null) {
                        sendError(Messages.CVSSCM_NoSuchJobExists(upName),req,rsp);
                        return;
                    }

                    Run build = p.getBuildByNumber(upstreams.get(p));
                    tagSet.put((AbstractBuild) build,tag);
                }
            }

            new TagWorkerThread(this,tagSet).start();

            doIndex(req,rsp);
        }

        /**
         * Checks if the given value is a valid CVS tag.
         *
         * If it's invalid, this method gives you the reason as string.
         */
        private String isInvalidTag(String name) {
            // source code from CVS rcs.c
            //void
            //RCS_check_tag (tag)
            //    const char *tag;
            //{
            //    char *invalid = "$,.:;@";		/* invalid RCS tag characters */
            //    const char *cp;
            //
            //    /*
            //     * The first character must be an alphabetic letter. The remaining
            //     * characters cannot be non-visible graphic characters, and must not be
            //     * in the set of "invalid" RCS identifier characters.
            //     */
            //    if (isalpha ((unsigned char) *tag))
            //    {
            //    for (cp = tag; *cp; cp++)
            //    {
            //        if (!isgraph ((unsigned char) *cp))
            //        error (1, 0, "tag `%s' has non-visible graphic characters",
            //               tag);
            //        if (strchr (invalid, *cp))
            //        error (1, 0, "tag `%s' must not contain the characters `%s'",
            //               tag, invalid);
            //    }
            //    }
            //    else
            //    error (1, 0, "tag `%s' must start with a letter", tag);
            //}
            if(name==null || name.length()==0)
                return Messages.CVSSCM_TagIsEmpty();

            char ch = name.charAt(0);
            if(!(('A'<=ch && ch<='Z') || ('a'<=ch && ch<='z')))
                return Messages.CVSSCM_TagNeedsToStartWithAlphabet();

            for( char invalid : "$,.:;@".toCharArray() ) {
                if(name.indexOf(invalid)>=0)
                    return Messages.CVSSCM_TagContainsIllegalChar(invalid);
            }

            return null;
        }

        /**
         * Performs tagging.
         */
        public void perform(String tagName, TaskListener listener) {
            File destdir = null;
            try {
                destdir = Util.createTempDir();

                // unzip the archive
                listener.getLogger().println(Messages.CVSSCM_ExpandingWorkspaceArchive(destdir));
                Expand e = new Expand();
                e.setProject(new org.apache.tools.ant.Project());
                e.setDest(destdir);
                e.setSrc(getArchiveFile(build));
                e.setTaskType("unzip");
                e.execute();

                // run cvs tag command
                listener.getLogger().println(Messages.CVSSCM_TaggingWorkspace());
                for (String m : getAllModulesNormalized()) {
                    FilePath path = new FilePath(destdir).child(m);
                    boolean isDir = path.isDirectory();

                    ArgumentListBuilder cmd = new ArgumentListBuilder();
                    cmd.add(getDescriptor().getCvsExeOrDefault(),"tag");
                    if(isDir) {
                        cmd.add("-R");
                    }
                    cmd.add(tagName);
                    if(!isDir) {
                        cmd.add(path.getName());
                        path = path.getParent();
                    }

                    if(!CVSSCM.this.run(new Launcher.LocalLauncher(listener),cmd,listener, path)) {
                        listener.getLogger().println(Messages.CVSSCM_TaggingFailed());
                        return;
                    }
                }

                // completed successfully
                onTagCompleted(tagName);
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
            }
        }

        /**
         * Atomically set the tag name and then be done with {@link TagWorkerThread}.
         */
        private synchronized void onTagCompleted(String tagName) {
            if(this.tagName!=null)
                this.tagName += ' '+tagName;
            else
                this.tagName = tagName;
            this.workerThread = null;
        }
    }

    public static final class TagWorkerThread extends TaskThread {
        private final Map<AbstractBuild,String> tagSet;

        public TagWorkerThread(TagAction owner,Map<AbstractBuild,String> tagSet) {
            super(owner,ListenerAndText.forMemory());
            this.tagSet = tagSet;
        }

        public synchronized void start() {
            for (Entry<AbstractBuild, String> e : tagSet.entrySet()) {
                TagAction ta = e.getKey().getAction(TagAction.class);
                if(ta!=null)
                    associateWith(ta);
            }

            super.start();
        }

        protected void perform(TaskListener listener) {
            for (Entry<AbstractBuild, String> e : tagSet.entrySet()) {
                TagAction ta = e.getKey().getAction(TagAction.class);
                if(ta==null) {
                    listener.error(e.getKey()+" doesn't have CVS tag associated with it. Skipping");
                    continue;
                }
                listener.getLogger().println(Messages.CVSSCM_TagginXasY(e.getKey(),e.getValue()));
                try {
                    e.getKey().keepLog();
                } catch (IOException x) {
                    x.printStackTrace(listener.error(Messages.CVSSCM_FailedToMarkForKeep(e.getKey())));
                }
                ta.perform(e.getValue(), listener);
                listener.getLogger().println();
            }
        }
    }

    /**
     * Temporary hack for assisting trouble-shooting.
     *
     * <p>
     * Setting this property to true would cause <tt>cvs log</tt> to dump a lot of messages.
     */
    public static boolean debug = false;

    // probe to figure out the CVS hang problem
    public static boolean noQuiet = Boolean.getBoolean(CVSSCM.class.getName()+".noQuiet");

    private static final long serialVersionUID = 1L;

    /**
     * True to avoid computing the changelog. Useful with ancient versions of CVS that doesn't support
     * the -d option in the log command. See #1346.
     */
    public static boolean skipChangeLog = Boolean.getBoolean(CVSSCM.class.getName()+".skipChangeLog");
}
