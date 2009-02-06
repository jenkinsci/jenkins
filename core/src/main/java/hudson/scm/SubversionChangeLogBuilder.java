/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Jean-Baptiste Quenot, Luca Domenico Milanesio, Renaud Bruyeron
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
import hudson.model.BuildListener;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.FilePath;
import hudson.util.IOException2;
import hudson.remoting.VirtualChannel;
import hudson.FilePath.FileCallable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.xml.SVNXMLLogHandler;
import org.xml.sax.helpers.LocatorImpl;

import javax.xml.transform.Result;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import java.io.IOException;
import java.io.PrintStream;
import java.io.File;
import java.util.Map;
import java.util.Collection;

/**
 * Builds <tt>changelog.xml</tt> for {@link SubversionSCM}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SubversionChangeLogBuilder {
    /**
     * Revisions of the workspace before the update/checkout.
     */
    private final Map<String,Long> previousRevisions;
    /**
     * Revisions of the workspace after the update/checkout.
     */
    private final Map<String,Long> thisRevisions;

    private final BuildListener listener;
    private final SubversionSCM scm;
    private final AbstractBuild<?,?> build;

    public SubversionChangeLogBuilder(AbstractBuild<?,?> build, BuildListener listener, SubversionSCM scm) throws IOException {
        previousRevisions = SubversionSCM.parseRevisionFile(build.getPreviousBuild());
        thisRevisions     = SubversionSCM.parseRevisionFile(build);
        this.listener = listener;
        this.scm = scm;
        this.build = build;

    }

    public boolean run(Collection<SubversionSCM.External> externals, Result changeLog) throws IOException, InterruptedException {
        boolean changelogFileCreated = false;

        final SVNClientManager manager = SubversionSCM.createSvnClientManager();
        try {
            SVNLogClient svnlc = manager.getLogClient();
            TransformerHandler th = createTransformerHandler();
            th.setResult(changeLog);
            SVNXMLLogHandler logHandler = new SVNXMLLogHandler(th);
            // work around for http://svnkit.com/tracker/view.php?id=175
            th.setDocumentLocator(DUMMY_LOCATOR);
            logHandler.startDocument();

            for (ModuleLocation l : scm.getLocations(build)) {
                changelogFileCreated |= buildModule(l.getURL(), svnlc, logHandler);
            }
            for(SubversionSCM.External ext : externals) {
                changelogFileCreated |= buildModule(
                        getUrlForPath(build.getProject().getWorkspace().child(ext.path)), svnlc, logHandler);
            }

            if(changelogFileCreated) {
                logHandler.endDocument();
            }

            return changelogFileCreated;
        } finally {
            manager.dispose();
        }
    }

    private String getUrlForPath(FilePath path) throws IOException, InterruptedException {
        return path.act(new GetUrlForPath(createAuthenticationProvider()));
    }

    private ISVNAuthenticationProvider createAuthenticationProvider() {
        return SubversionSCM.DescriptorImpl.DESCRIPTOR.createAuthenticationProvider();
    }

    private boolean buildModule(String url, SVNLogClient svnlc, SVNXMLLogHandler logHandler) throws IOException2 {
        PrintStream logger = listener.getLogger();
        Long prevRev = previousRevisions.get(url);
        if(prevRev==null) {
            logger.println("no revision recorded for "+url+" in the previous build");
            return false;
        }
        Long thisRev = thisRevisions.get(url);
        if (thisRev == null) {
            listener.error("No revision found for URL: " + url + " in " + SubversionSCM.getRevisionFile(build) + ". Revision file contains: " + thisRevisions.keySet());
            return true;
        }
        if(thisRev.equals(prevRev)) {
            logger.println("no change for "+url+" since the previous build");
            return false;
        }

        try {
            if(debug)
                listener.getLogger().printf("Computing changelog of %1s from %2s to %3s\n",
                        SVNURL.parseURIEncoded(url), prevRev+1, thisRev);
            svnlc.doLog(SVNURL.parseURIEncoded(url),
                        null,
                        SVNRevision.UNDEFINED,
                        SVNRevision.create(prevRev+1),
                        SVNRevision.create(thisRev),
                        false, // Don't stop on copy.
                        true, // Report paths.
                        0, // Retrieve log entries for unlimited number of revisions.
                        debug ? new DebugSVNLogHandler(logHandler) : logHandler);
            if(debug)
                listener.getLogger().println("done");
        } catch (SVNException e) {
            throw new IOException2("revision check failed on "+url,e);
        }
        return true;
    }

    /**
     * Filter {@link ISVNLogEntryHandler} that dumps information. Used only for debugging.
     */
    private class DebugSVNLogHandler implements ISVNLogEntryHandler {
        private final ISVNLogEntryHandler core;

        private DebugSVNLogHandler(ISVNLogEntryHandler core) {
            this.core = core;
        }

        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            listener.getLogger().println("SVNLogEntry="+logEntry);
            core.handleLogEntry(logEntry);
        }
    }

    /**
     * Creates an identity transformer.
     */
    private static TransformerHandler createTransformerHandler() {
        try {
            return ((SAXTransformerFactory) SAXTransformerFactory.newInstance()).newTransformerHandler();
        } catch (TransformerConfigurationException e) {
            throw new Error(e); // impossible
        }
    }

    private static final LocatorImpl DUMMY_LOCATOR = new LocatorImpl();

    public static boolean debug = false;

    static {
        DUMMY_LOCATOR.setLineNumber(-1);
        DUMMY_LOCATOR.setColumnNumber(-1);
    }

    private static class GetUrlForPath implements FileCallable<String> {
        private final ISVNAuthenticationProvider authProvider;

        public GetUrlForPath(ISVNAuthenticationProvider authProvider) {
            this.authProvider = authProvider;
        }

        public String invoke(File p, VirtualChannel channel) throws IOException {
            final SVNClientManager manager = SubversionSCM.createSvnClientManager(authProvider);
            try {
                final SVNWCClient svnwc = manager.getWCClient();

                SVNInfo info;
                try {
                    info = svnwc.doInfo(p, SVNRevision.WORKING);
                    return info.getURL().toDecodedString();
                } catch (SVNException e) {
                    e.printStackTrace();
                    return null;
                }
            } finally {
                manager.dispose();
            }
        }

        private static final long serialVersionUID = 1L;
    }
}
