package hudson.scm;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.model.LargeText;
import hudson.scm.SubversionSCM.SvnInfo;
import hudson.util.CopyOnWriteMap;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map.Entry;
import java.lang.ref.WeakReference;

/**
 * {@link Action} that lets people create tag for the given build.
 * 
 * @author Kohsuke Kawaguchi
 */
public class SubversionTagAction extends AbstractScmTagAction {

    /**
     * If non-null, that means the build is already tagged.
     * Map is from the repository URL to the URL of the tag.
     * If a module is not tagged, the value will be null.
     */
    private final Map<SvnInfo,String> tags = new CopyOnWriteMap.Tree<SvnInfo,String>();

    /*package*/ SubversionTagAction(AbstractBuild build,Collection<SvnInfo> svnInfos) {
        super(build);
        Map<SvnInfo,String> m = new HashMap<SvnInfo, String>();
        for (SvnInfo si : svnInfos)
            m.put(si,null);
        tags.putAll(m);
    }

    public String getIconFileName() {
        if(tags==null && !Hudson.isAdmin())
            return null;
        return "save.gif";
    }

    public String getDisplayName() {
        int nonNullTag = 0;
        for (String v : tags.values()) {
            if(v!=null) {
                nonNullTag++;
                if(nonNullTag>1)
                    break;
            }
        }
        if(nonNullTag==0)
            return "Tag this build";
        if(nonNullTag==1)
            return "Subversion tag";
        else
            return "Subversion tags";
    }

    /**
     * @see #tags
     */
    public Map<SvnInfo,String> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    private static final Pattern TRUNK_BRANCH_MARKER = Pattern.compile("/(trunk|branches)(/|$)");

    /**
     * Creates a URL, to be used as the default value of the module tag URL.
     *
     * @return
     *      null if failed to guess.
     */
    public String makeTagURL(SvnInfo si) {
        // assume the standard trunk/branches/tags repository layout
        Matcher m = TRUNK_BRANCH_MARKER.matcher(si.url);
        if(!m.find())
            return null;    // doesn't have 'trunk' nor 'branches'

        return si.url.substring(0,m.start())+"/tags/"+build.getProject().getName()+"-"+build.getNumber();
    }

    /**
     * Invoked to actually tag the workspace.
     */
    public synchronized void doSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        Map<SvnInfo,String> newTags = new HashMap<SvnInfo,String>();

        int i=-1;
        for (Entry<SvnInfo, String> e : tags.entrySet()) {
            i++;
            if(req.getParameter("tag"+i)==null)
                continue;
            newTags.put(e.getKey(),req.getParameter("name" + i));
        }

        new TagWorkerThread(newTags).start();

        rsp.sendRedirect(".");
    }

    /**
     * The thread that performs tagging operation asynchronously.
     */
    public final class TagWorkerThread extends AbstractTagWorkerThread {
        private final Map<SvnInfo,String> tagSet;

        public TagWorkerThread(Map<SvnInfo, String> tagSet) {
            this.tagSet = tagSet;
        }

        public synchronized void start() {
            SubversionTagAction.this.workerThread = this;
            SubversionTagAction.this.log = new WeakReference<LargeText>(text);
            super.start();
        }

        @Override
        protected void perform(TaskListener listener) {
            SVNClientManager cm = SubversionSCM.createSvnClientManager(SubversionSCM.DescriptorImpl.DESCRIPTOR.createAuthenticationProvider());

            for (Entry<SvnInfo, String> e : tagSet.entrySet()) {
                PrintStream logger = listener.getLogger();
                logger.println("Tagging "+e.getKey()+" to "+e.getValue());

                try {
                    SVNURL src = SVNURL.parseURIDecoded(e.getKey().url);
                    SVNURL dst = SVNURL.parseURIDecoded(e.getValue());

                    SVNCopyClient svncc = cm.getCopyClient();
                    svncc.doCopy(src, SVNRevision.create(e.getKey().revision), dst, false, true, "Tagged from "+build );
                } catch (SVNException x) {
                    x.printStackTrace(listener.error("Failed to tag"));
                }
            }
        }
    }
}
