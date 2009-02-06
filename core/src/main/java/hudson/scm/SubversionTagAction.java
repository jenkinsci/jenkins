/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jean-Baptiste Quenot, Seiji Sogabe, Vojtech Habarta
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

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.TaskListener;
import hudson.model.TaskThread;
import hudson.scm.SubversionSCM.SvnInfo;
import hudson.util.CopyOnWriteMap;
import hudson.security.Permission;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNCopySource;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link Action} that lets people create tag for the given build.
 * 
 * @author Kohsuke Kawaguchi
 */
public class SubversionTagAction extends AbstractScmTagAction {

    /**
     * Map is from the repository URL to the URLs of tags.
     * If a module is not tagged, the value will be empty list.
     * Never an empty map.
     */
    private final Map<SvnInfo,List<String>> tags = new CopyOnWriteMap.Tree<SvnInfo, List<String>>();

    /*package*/ SubversionTagAction(AbstractBuild build,Collection<SvnInfo> svnInfos) {
        super(build);
        Map<SvnInfo,List<String>> m = new HashMap<SvnInfo,List<String>>();
        for (SvnInfo si : svnInfos)
            m.put(si,new ArrayList<String>());
        tags.putAll(m);
    }

    /**
     * Was any tag created by the user already?
     */
    public boolean hasTags() {
        for (Entry<SvnInfo, List<String>> e : tags.entrySet())
            if(!e.getValue().isEmpty())
                return true;
        return false;
    }

    public String getIconFileName() {
        if(!hasTags() && !getACL().hasPermission(getPermission()))
            return null;
        return "save.gif";
    }

    public String getDisplayName() {
        int nonNullTag = 0;
        for (List<String> v : tags.values()) {
            if(!v.isEmpty()) {
                nonNullTag++;
                if(nonNullTag>1)
                    break;
            }
        }
        if(nonNullTag==0)
            return Messages.SubversionTagAction_DisplayName_HasNoTag();
        if(nonNullTag==1)
            return Messages.SubversionTagAction_DisplayName_HasATag();
        else
            return Messages.SubversionTagAction_DisplayName_HasTags();
    }

    /**
     * @see #tags
     */
    public Map<SvnInfo,List<String>> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    /**
     * Returns true if this build has already been tagged at least once.
     */
    @Override
    public boolean isTagged() {
        for (List<String> t : tags.values()) {
            if(!t.isEmpty())    return true;
        }
        return false;
    }

    @Override
    public String getTooltip() {
        if(isTagged())  return Messages.SubversionTagAction_Tooltip();
        else            return null;
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
        getACL().checkPermission(getPermission());

        Map<SvnInfo,String> newTags = new HashMap<SvnInfo,String>();

        int i=-1;
        for (SvnInfo e : tags.keySet()) {
            i++;
            if(tags.size()>1 && req.getParameter("tag"+i)==null)
                continue; // when tags.size()==1, UI won't show the checkbox.
            newTags.put(e,req.getParameter("name" + i));
        }

        new TagWorkerThread(newTags).start();

        rsp.sendRedirect(".");
    }

    @Override
    public Permission getPermission() {
        return SubversionSCM.TAG;
    }

    /**
     * The thread that performs tagging operation asynchronously.
     */
    public final class TagWorkerThread extends TaskThread {
        private final Map<SvnInfo,String> tagSet;

        public TagWorkerThread(Map<SvnInfo,String> tagSet) {
            super(SubversionTagAction.this,ListenerAndText.forMemory());
            this.tagSet = tagSet;
        }

        @Override
        protected void perform(TaskListener listener) {
            try {
                final SVNClientManager cm = SubversionSCM.createSvnClientManager();
                try {
                    for (Entry<SvnInfo, String> e : tagSet.entrySet()) {
                        PrintStream logger = listener.getLogger();
                        logger.println("Tagging "+e.getKey()+" to "+e.getValue());

                        try {
                            SVNURL src = SVNURL.parseURIDecoded(e.getKey().url);
                            SVNURL dst = SVNURL.parseURIDecoded(e.getValue());

                            SVNCopyClient svncc = cm.getCopyClient();
                            SVNRevision sourceRevision = SVNRevision.create(e.getKey().revision);
                            SVNCopySource csrc = new SVNCopySource(sourceRevision, sourceRevision, src);
                            svncc.doCopy(
                                    new SVNCopySource[]{csrc},
                                    dst, false, true, false, "Tagged from "+build, null );
                        } catch (SVNException x) {
                            x.printStackTrace(listener.error("Failed to tag"));
                            return;
                        }
                    }

                    // completed successfully
                    for (Entry<SvnInfo,String> e : tagSet.entrySet())
                        SubversionTagAction.this.tags.get(e.getKey()).add(e.getValue());
                    build.save();
                    workerThread = null;
                } finally {
                    cm.dispose();
                }
           } catch (Throwable e) {
               e.printStackTrace(listener.fatalError(e.getMessage()));
           }
        }
    }
}
