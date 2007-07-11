package hudson.scm;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.util.FormFieldValidator;
import hudson.util.IOException2;
import hudson.util.MultipartFormDataParser;
import hudson.util.Scrambler;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Chmod;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import javax.servlet.ServletException;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Subversion SCM.
 *
 * @author Kohsuke Kawaguchi
 */
public class SubversionSCM extends SCM implements Serializable {
    /**
     * the locations field is used to store all configured SVN locations (with
     * their local and remote part). Direct access to this filed should be
     * avoided and the getLocations() method should be used instead. This is
     * needed to make importing of old hudson-configurations possible as
     * getLocations() will check if the modules field has been set and import
     * the data.
     *
     * @since 1.91
     */
    private ModuleLocation[] locations = new ModuleLocation[0];

    private boolean useUpdate;
    private String username;
    private final SubversionRepositoryBrowser browser;

    // No longer in use but left for serialization compatibility.
    @Deprecated
    private String modules;

    SubversionSCM(String[] remoteLocations, String[] localLocations,
            boolean useUpdate, String username, SubversionRepositoryBrowser browser) {

        List<ModuleLocation> modules = new ArrayList<ModuleLocation>();
        if (remoteLocations != null && localLocations != null) {
            int entries = Math.min(remoteLocations.length, localLocations.length);

            for (int i = 0; i < entries; i++) {
                // the remote (repository) location
                String remoteLoc = nullify(remoteLocations[i]);

                if (remoteLoc != null) {// null if skipped
                    remoteLoc = Util.removeTrailingSlash(remoteLoc.trim());
                    modules.add(new ModuleLocation(remoteLoc, nullify(localLocations[i])));
                }
            }
        }
        locations = modules.toArray(new ModuleLocation[modules.size()]);

        this.useUpdate = useUpdate;
        this.username = nullify(username);
        this.browser = browser;
    }

    /**
     * @deprecated
     *      as of 1.91. Use {@link #getLocations()} instead.
     */
    public String getModules() {
        return null;
    }

    /**
     * list of all configured svn locations
     *
     * @since 1.91
     */
    public ModuleLocation[] getLocations() {
        // check if we've got a old location
        if (modules != null) {
            // import the old configuration
            List<ModuleLocation> oldLocations = new ArrayList<ModuleLocation>();
            StringTokenizer tokens = new StringTokenizer(modules);
            while (tokens.hasMoreTokens()) {
                // the remote (repository location)
                // the normalized name is always without the trailing '/'
                String remoteLoc = Util.removeTrailingSlash(tokens.nextToken());

                oldLocations.add(new ModuleLocation(remoteLoc, null));
            }

            locations = oldLocations.toArray(new ModuleLocation[oldLocations.size()]);
            modules = null;
        }
        return locations;
    }

    public boolean isUseUpdate() {
        return useUpdate;
    }

    public String getUsername() {
        return username;
    }

    public SubversionRepositoryBrowser getBrowser() {
        return browser;
    }

    /**
     * Called after checkout/update has finished to compute the changelog.
     */
    private boolean calcChangeLog(AbstractBuild<?,?> build, File changelogFile, BuildListener listener, List<String> externals) throws IOException, InterruptedException {
        if(build.getPreviousBuild()==null) {
            // nothing to compare against
            return createEmptyChangeLog(changelogFile, listener, "log");
        }

        // some users reported that the file gets created with size 0. I suspect
        // maybe some XSLT engine doesn't close the stream properly.
        // so let's do it by ourselves to be really sure that the stream gets closed.
        OutputStream os = new BufferedOutputStream(new FileOutputStream(changelogFile));
        boolean created;
        try {
            created = new SubversionChangeLogBuilder(build, listener, this).run(externals, new StreamResult(os));
        } finally {
            os.close();
        }
        if(!created)
            createEmptyChangeLog(changelogFile, listener, "log");

        return true;
    }


    /*package*/ static Map<String,Long> parseRevisionFile(AbstractBuild build) throws IOException {
        Map<String,Long> revisions = new HashMap<String,Long>(); // module -> revision
        {// read the revision file of the last build
            File file = getRevisionFile(build);
            if(!file.exists())
                // nothing to compare against
                return revisions;

            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while((line=br.readLine())!=null) {
                int index = line.lastIndexOf('/');
                if(index<0) {
                    continue;   // invalid line?
                }
                try {
                    revisions.put(line.substring(0,index), Long.parseLong(line.substring(index+1)));
                } catch (NumberFormatException e) {
                    // perhaps a corrupted line. ignore
                }
            }
        }

        return revisions;
    }

    /**
     * Parses the file that stores the locations in the workspace where modules loaded by svn:external
     * is placed.
     */
    /*package*/ static List<String> parseExternalsFile(AbstractProject project) throws IOException {
        List<String> ext = new ArrayList<String>(); // workspace-relative path

        {// read the revision file of the last build
            File file = getExternalsFile(project);
            if(!file.exists())
                // nothing to compare against
                return ext;

            BufferedReader br = new BufferedReader(new FileReader(file));
            try {
            	String line;
            	while((line=br.readLine())!=null) {
            		ext.add(line);
            	}
            } finally {
            	br.close();
            }
        }

        return ext;
    }

    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, final BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        List<String> externals = checkout(build.getTimestamp().getTime(),workspace,listener);

        if(externals==null)
            return false;

        // write out the revision file
        PrintWriter w = new PrintWriter(new FileOutputStream(getRevisionFile(build)));
        try {
            Map<String,SvnInfo> revMap = buildRevisionMap(workspace,listener,externals);
            for (Entry<String,SvnInfo> e : revMap.entrySet()) {
                w.println( e.getKey() +'/'+ e.getValue().revision );
            }
        } finally {
            w.close();
        }

        // write out the externals info
        w = new PrintWriter(new FileOutputStream(getExternalsFile(build.getProject())));
        try {
            for (String p : externals) {
                w.println( p );
            }
        } finally {
            w.close();
        }

        return calcChangeLog(build, changelogFile, listener, externals);
    }

    /**
     * Performs the checkout or update, depending on the configuration and workspace state.
     *
     * @return null
     *      if the operation failed. Otherwise the set of local workspace paths
     *      (relative to the workspace root) that has loaded due to svn:external.
     */
    private List<String> checkout(final Date timestamp, FilePath workspace, final TaskListener listener) throws IOException, InterruptedException {
        final ISVNAuthenticationProvider authProvider = getDescriptor().createAuthenticationProvider();

        // true to "svn update", false to "svn checkout".
        final boolean update = useUpdate && isUpdatable(workspace, listener);

        return workspace.act(new FileCallable<List<String>>() {
            public List<String> invoke(File ws, VirtualChannel channel) throws IOException {
                SVNUpdateClient svnuc = createSvnClientManager(authProvider).getUpdateClient();
                List<String> externals = new ArrayList<String>(); // store discovered externals to here
                SVNRevision revision = SVNRevision.create(timestamp);
                if(update) {

                    for (ModuleLocation l : getLocations()) {
                        try {
                            listener.getLogger().println("Updating "+ l.remote);

                            svnuc.setEventHandler(new SubversionUpdateEventHandler(listener, externals, l.local));
                            svnuc.doUpdate(new File(ws, l.local), revision, true);

                        } catch (SVNException e) {
                            e.printStackTrace(listener.error("Failed to update "+l.remote));
                            return null;
                        }
                    }
                } else {
                    Util.deleteContentsRecursive(ws);

                    for (ModuleLocation l : getLocations()) {
                        try {
                            SVNURL url = SVNURL.parseURIEncoded(l.remote);
                            listener.getLogger().println("Checking out "+url);

                            svnuc.setEventHandler(new SubversionUpdateEventHandler(listener, externals, l.local));
                            svnuc.doCheckout(url, new File(ws, l.local), SVNRevision.HEAD, revision, true);

                        } catch (SVNException e) {
                            e.printStackTrace(listener.error("Failed to check out "+l.remote));
                            return null;
                        }
                    }
                }
                return externals;
            }
        });
    }

    /**
     * Creates {@link SVNClientManager}.
     *
     * <p>
     * This method must be executed on the slave where svn operations are performed.
     *
     * @param authProvider
     *      The value obtained from {@link DescriptorImpl#createAuthenticationProvider()}.
     *      If the operation runs on slaves,
     *      (and properly remoted, if the svn operations run on slaves.)
     */
    /*package*/ static SVNClientManager createSvnClientManager(ISVNAuthenticationProvider authProvider) {
        ISVNAuthenticationManager sam = SVNWCUtil.createDefaultAuthenticationManager();
        sam.setAuthenticationProvider(authProvider);
        return SVNClientManager.newInstance(SVNWCUtil.createDefaultOptions(true),sam);
    }

    public static final class SvnInfo implements Serializable, Comparable<SvnInfo> {
        /**
         * Decoded repository URL.
         */
        final String url;
        final long revision;

        public SvnInfo(String url, long revision) {
            this.url = url;
            this.revision = revision;
        }

        public SvnInfo(SVNInfo info) {
            this( info.getURL().toDecodedString(), info.getCommittedRevision().getNumber() );
        }

        public SVNURL getSVNURL() throws SVNException {
            return SVNURL.parseURIDecoded(url);
        }

        public int compareTo(SvnInfo that) {
            int d = this.url.compareTo(that.url);
            if(d!=0)    return d;
            long e = this.revision-that.revision;
            if(e<0) return -1;
            if(e>0) return 1;
            return 0;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Gets the SVN metadata for the given local workspace.
     *
     * @param workspace
     *      The target to run "svn info".
     */
    private SVNInfo parseSvnInfo(File workspace, ISVNAuthenticationProvider authProvider) throws SVNException {
        SVNWCClient svnWc = createSvnClientManager(authProvider).getWCClient();
        return svnWc.doInfo(workspace,SVNRevision.WORKING);
    }

    /**
     * Gets the SVN metadata for the remote repository.
     *
     * @param remoteUrl
     *      The target to run "svn info".
     */
    private SVNInfo parseSvnInfo(SVNURL remoteUrl, ISVNAuthenticationProvider authProvider) throws SVNException {
        SVNWCClient svnWc = createSvnClientManager(authProvider).getWCClient();
        return svnWc.doInfo(remoteUrl, SVNRevision.HEAD, SVNRevision.HEAD);
    }

    /**
     * Checks .svn files in the workspace and finds out revisions of the modules
     * that the workspace has.
     *
     * @return
     *      null if the parsing somehow fails. Otherwise a map from the repository URL to revisions.
     */
    private Map<String,SvnInfo> buildRevisionMap(FilePath workspace, final TaskListener listener, final List<String> externals) throws IOException, InterruptedException {
        final ISVNAuthenticationProvider authProvider = getDescriptor().createAuthenticationProvider();
        return workspace.act(new FileCallable<Map<String,SvnInfo>>() {
            public Map<String,SvnInfo> invoke(File ws, VirtualChannel channel) throws IOException {
                Map<String/*module name*/,SvnInfo> revisions = new HashMap<String,SvnInfo>();

                SVNWCClient svnWc = createSvnClientManager(authProvider).getWCClient();
                // invoke the "svn info"
                for( ModuleLocation module : getLocations() ) {
                    try {
                        SvnInfo info = new SvnInfo(svnWc.doInfo(new File(ws,module.local),SVNRevision.WORKING));
                        revisions.put(info.url,info);
                    } catch (SVNException e) {
                        e.printStackTrace(listener.error("Failed to parse svn info for "+module));
                    }
                }
                for(String local : externals){
                    try {
                        SvnInfo info = new SvnInfo(svnWc.doInfo(new File(ws, local),SVNRevision.WORKING));
                        revisions.put(info.url,info);
                    } catch (SVNException e) {
                        e.printStackTrace(listener.error("Failed to parse svn info for external "+local));
                    }

                }

                return revisions;
            }
        });
    }

    /**
     * Gets the file that stores the revision.
     */
    private static File getRevisionFile(AbstractBuild build) {
        return new File(build.getRootDir(),"revision.txt");
    }

    /**
     * Gets the file that stores the externals.
     */
    private static File getExternalsFile(AbstractProject project) {
        return new File(project.getRootDir(),"svnexternals.txt");
    }

    /**
     * Returns true if we can use "svn update" instead of "svn checkout"
     */
    private boolean isUpdatable(FilePath workspace, final TaskListener listener) throws IOException, InterruptedException {
        final ISVNAuthenticationProvider authProvider = getDescriptor().createAuthenticationProvider();

        return workspace.act(new FileCallable<Boolean>() {
            public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
                for (ModuleLocation l : getLocations()) {
                    String url = l.remote;
                    String moduleName = l.local;
                    File module = new File(ws,moduleName).getCanonicalFile(); // canonicalize to remove ".." and ".". See #474

                    if(!module.exists()) {
                        listener.getLogger().println("Checking out a fresh workspace because "+module+" doesn't exist");
                        return false;
                    }

                    try {
                        SvnInfo svnInfo = new SvnInfo(parseSvnInfo(module,authProvider));
                        if(!svnInfo.url.equals(url)) {
                            listener.getLogger().println("Checking out a fresh workspace because the workspace is not "+url);
                            return false;
                        }
                    } catch (SVNException e) {
                        listener.getLogger().println("Checking out a fresh workspace because Hudson failed to detect the current workspace "+module);
                        e.printStackTrace(listener.error(e.getMessage()));
                        return false;
                    }
                }
                return true;
            }
        });
    }

    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {

    	// we need to load the externals info, if any
    	List<String> externals = parseExternalsFile(project);

        // current workspace revision
        Map<String,SvnInfo> wsRev = buildRevisionMap(workspace,listener,externals);

        ISVNAuthenticationProvider authProvider = getDescriptor().createAuthenticationProvider();

        // check the corresponding remote revision
        for (SvnInfo localInfo : wsRev.values()) {
            try {
                SvnInfo remoteInfo = new SvnInfo(parseSvnInfo(localInfo.getSVNURL(),authProvider));
                listener.getLogger().println("Revision:"+remoteInfo.revision);
                if(remoteInfo.revision > localInfo.revision)
                    return true;    // change found
            } catch (SVNException e) {
                e.printStackTrace(listener.error("Failed to check repository revision for "+localInfo.url));
            }
        }

        return false; // no change
    }

    public ChangeLogParser createChangeLogParser() {
        return new SubversionChangeLogParser();
    }


    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public FilePath getModuleRoot(FilePath workspace) {
        if (getLocations().length > 0)
            return workspace.child(getLocations()[0].local);
        return workspace;
    }

    private static String getLastPathComponent(String s) {
        String[] tokens = s.split("/");
        return tokens[tokens.length-1]; // return the last token
    }

    public static final class DescriptorImpl extends SCMDescriptor<SubversionSCM> {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        /**
         * SVN authentication realm to its associated credentials.
         */
        private final Map<String,Credential> credentials = new Hashtable<String,Credential>();

        /**
         * Stores {@link SVNAuthentication} for a single realm.
         *
         * <p>
         * {@link Credential} holds data in a persistence-friendly way,
         * and it's capable of creating {@link SVNAuthentication} object,
         * to be passed to SVNKit.
         */
        private static abstract class Credential implements Serializable {
            /**
             * @param kind
             *      One of the constants defined in {@link ISVNAuthenticationManager},
             *      indicating what subype of {@link SVNAuthentication} is expected.
             */
            abstract SVNAuthentication createSVNAuthentication(String kind) throws SVNException;
        }

        /**
         * Username/password based authentication.
         */
        private static final class PasswordCredential extends Credential {
            private final String userName;
            private final String password; // scrambled by base64

            public PasswordCredential(String userName, String password) {
                this.userName = userName;
                this.password = Scrambler.scramble(password);
            }

            @Override
            SVNAuthentication createSVNAuthentication(String kind) {
                if(kind.equals(ISVNAuthenticationManager.SSH))
                    return new SVNSSHAuthentication(userName,Scrambler.descramble(password),-1,false);
                else
                    return new SVNPasswordAuthentication(userName,Scrambler.descramble(password),false);
            }
        }

        /**
         * Publickey authentication for Subversion over SSH.
         */
        private static final class SshPublicKeyCredential extends Credential {
            private final String userName;
            private final String passphrase; // scrambled by base64
            private final String id;

            /**
             * @param keyFile
             *      stores SSH private key. The file will be copied.
             */
            public SshPublicKeyCredential(String userName, String passphrase, File keyFile) throws SVNException {
                this.userName = userName;
                this.passphrase = Scrambler.scramble(passphrase);

                Random r = new Random();
                StringBuilder buf = new StringBuilder();
                for(int i=0;i<16;i++)
                    buf.append(Integer.toHexString(r.nextInt(16)));
                this.id = buf.toString();

                try {
                    FileUtils.copyFile(keyFile,getKeyFile());
                } catch (IOException e) {
                    throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE,"Unable to save private key"),e);
                }
            }

            /**
             * Gets the location where the private key will be permanently stored.
             */
            private File getKeyFile() {
                File dir = new File(Hudson.getInstance().getRootDir(),"subversion-credentials");
                if(dir.mkdirs()) {
                    // make sure the directory exists. if we created it, try to set the permission to 600
                    // since this is sensitive information
                    try {
                        Chmod chmod = new Chmod();
                        chmod.setProject(new Project());
                        chmod.setFile(dir);
                        chmod.setPerm("600");
                        chmod.execute();
                    } catch (Throwable e) {
                        // if we failed to set the permission, that's fine.
                        LOGGER.log(Level.WARNING, "Failed to set directory permission of "+dir,e);
                    }
                }
                return new File(dir,id);
            }

            @Override
            SVNSSHAuthentication createSVNAuthentication(String kind) throws SVNException {
                if(kind.equals(ISVNAuthenticationManager.SSH)) {
                    try {
                        Channel channel = Channel.current();
                        String privateKey;
                        if(channel!=null) {
                            // remote
                            privateKey = channel.call(new Callable<String,IOException>() {
                                public String call() throws IOException {
                                    return FileUtils.readFileToString(getKeyFile(),"iso-8859-1");
                                }
                            });
                        } else {
                            privateKey = FileUtils.readFileToString(getKeyFile(),"iso-8859-1");
                        }
                        return new SVNSSHAuthentication(userName, privateKey.toCharArray(), Scrambler.descramble(passphrase),-1,false);
                    } catch (IOException e) {
                        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE,"Unable to load private key"),e);
                    } catch (InterruptedException e) {
                        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE,"Unable to load private key"),e);
                    }
                } else
                    return null; // unknown
            }
        }

        /**
         * SSL client certificate based authentication.
         */
        private static final class SslClientCertificateCredential extends Credential {
            private final String password; // scrambled by base64

            public SslClientCertificateCredential(File certificate, String password) {
                this.password = Scrambler.scramble(password);
            }

            @Override
            SVNAuthentication createSVNAuthentication(String kind) {
                if(kind.equals(ISVNAuthenticationManager.SSL))
                    return new SVNSSLAuthentication(null,Scrambler.descramble(password),false);
                else
                    return null; // unexpected authentication type
            }
        }

        /**
         * Remoting interface that allows remote {@link ISVNAuthenticationProvider}
         * to read from local {@link DescriptorImpl#credentials}.
         */
        private interface RemotableSVNAuthenticationProvider {
            Credential getCredential(String realm);
        }

        private final class RemotableSVNAuthenticationProviderImpl implements RemotableSVNAuthenticationProvider, Serializable {
            public Credential getCredential(String realm) {
                LOGGER.fine(String.format("getCredential(%s)=>%s",realm,credentials.get(realm)));
                return credentials.get(realm);
            }

            /**
             * When sent to the remote node, send a proxy.
             */
            private Object writeReplace() {
                return Channel.current().export(RemotableSVNAuthenticationProvider.class, this);
            }
        }

        /**
         * See {@link DescriptorImpl#createAuthenticationProvider()}.
         */
        private static final class SVNAuthenticationProviderImpl implements ISVNAuthenticationProvider, Serializable {
            private final RemotableSVNAuthenticationProvider source;

            public SVNAuthenticationProviderImpl(RemotableSVNAuthenticationProvider source) {
                this.source = source;
            }

            public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
                Credential cred = source.getCredential(realm);
                LOGGER.fine(String.format("requestClientAuthentication(%s,%s,%s)=>%s",kind,url,realm,cred));
                if(cred==null)  return null;
                try {
                    return cred.createSVNAuthentication(kind);
                } catch (SVNException e) {
                    logger.log(Level.SEVERE, "Failed to authorize",e);
                    throw new RuntimeException("Failed to authorize",e);
                }
            }

            public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
                return ACCEPTED_TEMPORARY;
            }

            private static final long serialVersionUID = 1L;
        }

        private DescriptorImpl() {
            super(SubversionSCM.class,SubversionRepositoryBrowser.class);
            load();
        }

        public String getDisplayName() {
            return "Subversion";
        }

        public SCM newInstance(StaplerRequest req) throws FormException {
            return new SubversionSCM(
                req.getParameterValues("svn.location_remote"),
                req.getParameterValues("svn.location_local"),
                req.getParameter("svn_use_update") != null,
                req.getParameter("svn_username"),
                RepositoryBrowsers.createInstance(SubversionRepositoryBrowser.class, req, "svn.browser"));
        }

        /**
         * Creates {@link ISVNAuthenticationProvider} backed by {@link #credentials}.
         * This method must be invoked on the master, but the returned object is remotable.
         */
        public ISVNAuthenticationProvider createAuthenticationProvider() {
            return new SVNAuthenticationProviderImpl(new RemotableSVNAuthenticationProviderImpl());
        }

        /**
         * Submits the authentication info.
         *
         * This code is fairly ugly because of the way SVNKit handles credentials.
         */
        public void doPostCredential(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            if(!Hudson.adminCheck(req,rsp)) return;

            MultipartFormDataParser parser = new MultipartFormDataParser(req);

            String url = parser.get("url");

            String kind = parser.get("kind");
            int idx = Arrays.asList("","password","publickey","certificate").indexOf(kind);

            final String username = parser.get("username"+idx);
            final String password = parser.get("password"+idx);


            // SVNKit wants a key in a file
            final File keyFile;
            FileItem item=null;
            if(kind.equals("password")) {
                keyFile = null;
            } else {
                item = parser.getFileItem(kind.equals("publickey")?"privateKey":"certificate");
                keyFile = File.createTempFile("hudson","key");
                if(item!=null)
                    try {
                        item.write(keyFile);
                    } catch (Exception e) {
                        throw new IOException2(e);
                    }
            }


            try {
                // the way it works with SVNKit is that
                // 1) svnkit calls AuthenticationManager asking for a credential.
                //    this is when we can see the 'realm', which identifies the user domain.
                // 2) DefaultSVNAuthenticationManager returns the username and password we set below
                // 3) if the authentication is successful, svnkit calls back acknowledgeAuthentication
                //    (so we store the password info here)
                SVNRepository repository = SVNRepositoryFactory.create(SVNURL.parseURIDecoded(url));
                repository.setAuthenticationManager(new DefaultSVNAuthenticationManager(SVNWCUtil.getDefaultConfigurationDirectory(),true,username,password,keyFile,password) {
                    Credential cred = null;

                    @Override
                    public SVNAuthentication getFirstAuthentication(String kind, String realm, SVNURL url) throws SVNException {
                        if(kind.equals(ISVNAuthenticationManager.PASSWORD))
                            cred = new PasswordCredential(username,password);
                        if(kind.equals(ISVNAuthenticationManager.SSH)) {
                            if(keyFile==null)
                                cred = new PasswordCredential(username,password);
                            else
                                cred = new SshPublicKeyCredential(username,password,keyFile);
                        }
                        if(kind.equals(ISVNAuthenticationManager.SSL))
                            cred = new SslClientCertificateCredential(keyFile,password);

                        if(cred==null)  return null;
                        return cred.createSVNAuthentication(kind);
                    }

                    @Override
                    public void acknowledgeAuthentication(boolean accepted, String kind, String realm, SVNErrorMessage errorMessage, SVNAuthentication authentication) throws SVNException {
                        if(accepted) {
                            assert cred!=null;
                            credentials.put(realm,cred);
                            save();
                        }
                        super.acknowledgeAuthentication(accepted, kind, realm, errorMessage, authentication);
                    }
                });
                repository.testConnection();
                rsp.sendRedirect("credentialOK");
            } catch (SVNException e) {
                req.setAttribute("message",e.getErrorMessage());
                rsp.forward(Hudson.getInstance(),"error",req);
            } finally {
                if(keyFile!=null)
                    keyFile.delete();
                if(item!=null)
                    item.delete();
            }
        }

        /**
         * validate the value for a remote (repository) location.
         */
        public void doSvnRemoteLocationCheck(final StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            // this can be used to hit any accessible URL, so limit that to admins
            new FormFieldValidator(req, rsp, true) {
                protected void check() throws IOException, ServletException {
                    // syntax check first
                    String url = Util.nullify(request.getParameter("value"));
                    if (url == null) {
                        ok(); // not entered yet
                        return;
                    }

                    // remove unneeded whitespaces
                    url = url.trim();
                    if(!URL_PATTERN.matcher(url).matches()) {
                        errorWithMarkup("Invalid URL syntax. See "
                            + "<a href=\"http://svnbook.red-bean.com/en/1.2/svn-book.html#svn.basic.in-action.wc.tbl-1\">this</a> "
                            + "for information about valid URLs.");
                        return;
                    }

                    // test the connection
                    try {
                        SVNRepository repository = SVNRepositoryFactory.create(SVNURL.parseURIDecoded(url));

                        ISVNAuthenticationManager sam = SVNWCUtil.createDefaultAuthenticationManager();
                        sam.setAuthenticationProvider(createAuthenticationProvider());
                        repository.setAuthenticationManager(sam);

                        repository.testConnection();
                        ok();
                    } catch (SVNException e) {
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));

                        String message="";
                        message += "Unable to access "+url+" : "+Util.escape( e.getErrorMessage().getFullMessage());
                        message += " <a href='#' id=svnerrorlink onclick='javascript:" +
                            "document.getElementById(\"svnerror\").style.display=\"block\";" +
                            "document.getElementById(\"svnerrorlink\").style.display=\"none\";" +
                            "return false;'>(show details)</a>";
                        message += "<pre id=svnerror style='display:none'>"+sw+"</pre>";
                        message += " (Maybe you need to <a href='"+req.getContextPath()+"/scm/SubversionSCM/enterCredential?"+url+"'>enter credential</a>?)";
                        message += "<br>";
                        logger.log(Level.INFO, "Failed to access subversion repository "+url,e);
                        errorWithMarkup(message);
                    }
                }
            }.process();
        }

        /**
         * validate the value for a local location (local checkout directory).
         */
        public void doSvnLocalLocationCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator(req, rsp, false) {
                protected void check() throws IOException, ServletException {
                    String v = Util.nullify(request.getParameter("value"));
                    if (v == null) {
                        // local directory is optional so this is ok
                        ok();
                        return;
                    }

                    v = v.trim();

                    // check if a absolute path has been supplied
                    // (the last check with the regex will match windows drives)
                    if (v.startsWith("/") || v.startsWith("\\") || v.startsWith("..") || v.matches("^[A-Za-z]:")) {
                        error("absolute path is not allowed");
                    }

                    // all tests passed so far
                    ok();
                }
            }.process();
        }

        static {
            new Initializer();
        }
    }

    static final Pattern URL_PATTERN = Pattern.compile("(https?|svn(\\+[a-z0-9]+)?|file)://.+");

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(SubversionSCM.class.getName());

    static {
        new Initializer();
    }

    private static final class Initializer {
        static {
            DAVRepositoryFactory.setup();   // http, https
            SVNRepositoryFactoryImpl.setup();   // svn, svn+xxx
            FSRepositoryFactory.setup();    // file
        }
    }

    /**
     * small structure to store local and remote (repository) location
     * information of the repository. As a addition it holds the invalid field
     * to make failure messages when doing a checkout possible
     */
    public static final class ModuleLocation implements Serializable {
        public final String remote;
        public final String local;

        public ModuleLocation(String remote, String local) {
            if(local==null)
                local = getLastPathComponent(remote);

            this.remote = remote.trim();
            this.local = local.trim();
        }

        private static final long serialVersionUID = 1L;
    }

    private static final Logger LOGGER = Logger.getLogger(SubversionSCM.class.getName());
}
