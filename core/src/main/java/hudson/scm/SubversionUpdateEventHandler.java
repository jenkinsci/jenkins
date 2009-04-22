/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, David Seymore, Renaud Bruyeron
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

import hudson.remoting.Which;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.List;

/**
 * Just prints out the progress of svn update/checkout operation in a way similar to
 * the svn CLI.
 *
 * This code also records all the referenced external locations.
 */
final class SubversionUpdateEventHandler extends SubversionEventHandlerImpl {

    /**
     * External urls that are fetched through svn:externals.
     * We add to this collection as we find them.
     */
    private final List<SubversionSCM.External> externals;
    /**
     * Relative path from the workspace root to the module root. 
     */
    private final String modulePath;
    
    public SubversionUpdateEventHandler(PrintStream out, List<SubversionSCM.External> externals, File moduleDir, String modulePath) {
        super(out,moduleDir);
        this.externals = externals;
        this.modulePath = modulePath;
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        File file = event.getFile();
        String path = null;
        if (file != null) {
            try {
                path = getRelativePath(file);
            } catch (IOException e) {
                throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_GENERAL), e);
            }
            path = getLocalPath(path);
        }

        /*
         * Gets the current action. An action is represented by SVNEventAction.
         * In case of an update an  action  can  be  determined  via  comparing
         * SVNEvent.getAction() and SVNEventAction.UPDATE_-like constants.
         */
        SVNEventAction action = event.getAction();
        if (action == SVNEventAction.UPDATE_EXTERNAL) {
            // for externals definitions
            SVNExternal ext = event.getExternalInfo();
            if(ext==null) {
                // prepare for the situation where the user created their own svnkit
                URL jarFile = null;
                try {
                    jarFile = Which.jarURL(SVNEvent.class);
                } catch (IOException e) {
                    // ignore this failure
                }
                out.println("AssertionError: appears to be using unpatched svnkit at "+ jarFile);
            } else {
                out.println(Messages.SubversionUpdateEventHandler_FetchExternal(
                        ext.getResolvedURL(), ext.getRevision().getNumber(), event.getFile()));
                //#1539 - an external inside an external needs to have the path appended 
                externals.add(new SubversionSCM.External(modulePath + "/" + path.substring(0
                		,path.length() - ext.getPath().length())
                		,ext));
            }
            return;
        }

        super.handleEvent(event,progress);
    }

    public void checkCancelled() throws SVNCancelException {
        if(Thread.interrupted())
            throw new SVNCancelException();
    }
}