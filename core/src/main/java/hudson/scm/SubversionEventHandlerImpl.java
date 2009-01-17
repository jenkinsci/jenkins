/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package hudson.scm;

import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNEventAdapter;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;

import java.io.File;
import java.io.PrintStream;

/**
 * {@link ISVNEventHandler} that emulates the SVN CLI behavior.
 *
 * @author Kohsuke Kawaguchi
 */
public class SubversionEventHandlerImpl extends SVNEventAdapter {
    protected final PrintStream out;

    protected final File baseDir;

    public SubversionEventHandlerImpl(PrintStream out, File baseDir) {
        this.out = out;
        this.baseDir = baseDir;
    }

    public void handleEvent(SVNEvent event, double progress) {
        File file = event.getFile();
        String path = null;
        if (file != null) {
            path = getRelativePath(file);
            path = getLocalPath(path);
        }

        SVNEventAction action = event.getAction();

        {// commit notifications
            if (action == SVNEventAction.COMMIT_ADDED) {
                out.println("Adding         "+path);
                return;
            }
            if (action == SVNEventAction.COMMIT_DELETED) {
                out.println("Deleting       "+path);
                return;
            }
            if (action == SVNEventAction.COMMIT_MODIFIED) {
                out.println("Sending        "+path);
                return;
            }
            if (action == SVNEventAction.COMMIT_REPLACED) {
                out.println("Replacing      "+path);
                return;
            }
            if (action == SVNEventAction.COMMIT_DELTA_SENT) {
                out.println("Transmitting file data....");
                return;
            }
        }

        String pathChangeType = " ";
        if (action == SVNEventAction.UPDATE_ADD) {
            pathChangeType = "A";
            SVNStatusType contentsStatus = event.getContentsStatus();
            if(contentsStatus== SVNStatusType.UNCHANGED) {
                // happens a lot with merges
                pathChangeType = " ";
            }else if (contentsStatus == SVNStatusType.CONFLICTED) {
                pathChangeType = "C";
            } else if (contentsStatus == SVNStatusType.MERGED) {
                pathChangeType = "G";
            }
        } else if (action == SVNEventAction.UPDATE_DELETE) {
            pathChangeType = "D";
        } else if (action == SVNEventAction.UPDATE_UPDATE) {
            SVNStatusType contentsStatus = event.getContentsStatus();
            if (contentsStatus == SVNStatusType.CHANGED) {
                /*
                 * the  item  was  modified in the repository (got  the changes
                 * from the repository
                 */
                pathChangeType = "U";
            }else if (contentsStatus == SVNStatusType.CONFLICTED) {
                /*
                 * The file item is in  a  state  of Conflict. That is, changes
                 * received from the repository during an update, overlap  with
                 * local changes the user has in his working copy.
                 */
                pathChangeType = "C";
            } else if (contentsStatus == SVNStatusType.MERGED) {
                /*
                 * The file item was merGed (those  changes that came from  the
                 * repository  did  not  overlap local changes and were  merged
                 * into the file).
                 */
                pathChangeType = "G";
            }
        } else if (action == SVNEventAction.UPDATE_COMPLETED) {
            // finished updating
            out.println("At revision " + event.getRevision());
            return;
        } else if (action == SVNEventAction.ADD){
            out.println("A     " + path);
            return;
        } else if (action == SVNEventAction.DELETE){
            out.println("D     " + path);
            return;
        } else if (action == SVNEventAction.LOCKED){
            out.println("L     " + path);
            return;
        } else if (action == SVNEventAction.LOCK_FAILED){
            out.println("failed to lock    " + path);
            return;
        }

        /*
         * Now getting the status of properties of an item. SVNStatusType  also
         * contains information on the properties state.
         */
        SVNStatusType propertiesStatus = event.getPropertiesStatus();
        String propertiesChangeType = " ";
        if (propertiesStatus == SVNStatusType.CHANGED) {
            propertiesChangeType = "U";
        } else if (propertiesStatus == SVNStatusType.CONFLICTED) {
            propertiesChangeType = "C";
        } else if (propertiesStatus == SVNStatusType.MERGED) {
            propertiesChangeType = "G";
        }

        String lockLabel = " ";
        SVNStatusType lockType = event.getLockStatus();
        if (lockType == SVNStatusType.LOCK_UNLOCKED) {
            // The lock is broken by someone.
            lockLabel = "B";
        }

        if(pathChangeType.equals(" ") && propertiesChangeType.equals(" ") && lockLabel.equals(" "))
            // nothing to display here.
            return;

        out.println(pathChangeType
                + propertiesChangeType
                + lockLabel
                + "       "
                + path);
    }

    public String getRelativePath(File file) {
        String inPath = file.getAbsolutePath().replace(File.separatorChar, '/');
        String basePath = baseDir.getAbsolutePath().replace(File.separatorChar, '/');
        String commonRoot = getCommonAncestor(inPath, basePath);
        if (commonRoot != null) {
            if (equals(inPath , commonRoot)) {
                return "";
            } else if (startsWith(inPath, commonRoot + "/")) {
                return inPath.substring(commonRoot.length() + 1);
            }
        }
        return inPath;
    }

    private static String getCommonAncestor(String p1, String p2) {
        if (SVNFileUtil.isWindows || SVNFileUtil.isOpenVMS) {
            String ancestor = SVNPathUtil.getCommonPathAncestor(p1.toLowerCase(), p2.toLowerCase());
            if (equals(ancestor, p1)) {
                return p1;
            } else if (equals(ancestor, p2)) {
                return p2;
            } else if (startsWith(p1, ancestor)) {
                return p1.substring(0, ancestor.length());
            }
            return ancestor;
        }
        return SVNPathUtil.getCommonPathAncestor(p1, p2);
    }

    private static boolean startsWith(String p1, String p2) {
        if (SVNFileUtil.isWindows || SVNFileUtil.isOpenVMS) {
            return p1.toLowerCase().startsWith(p2.toLowerCase());
        }
        return p1.startsWith(p2);
    }

    private static boolean equals(String p1, String p2) {
        if (SVNFileUtil.isWindows || SVNFileUtil.isOpenVMS) {
            return p1.toLowerCase().equals(p2.toLowerCase());
        }
        return p1.equals(p2);
    }

    public static String getLocalPath(String path) {
        path = path.replace('/', File.separatorChar);
        if ("".equals(path)) {
            path = ".";
        }
        return path;
    }
}
