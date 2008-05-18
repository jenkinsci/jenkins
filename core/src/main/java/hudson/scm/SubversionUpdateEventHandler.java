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

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.PrintStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import hudson.remoting.Which;

/**
 * Just prints out the progress of svn update/checkout operation in a way similar to
 * the svn CLI.
 *
 * This code also records all the referenced external locations.
 */
final class SubversionUpdateEventHandler implements ISVNEventHandler {

    private final PrintStream out;
    
    /**
     * External urls that are fetched through svn:externals.
     * We add to this collection as we find them.
     */
    private final List<SubversionSCM.External> externals;
    private final String modulePath;
    
    public SubversionUpdateEventHandler(PrintStream out, List<SubversionSCM.External> externals, String modulePath) {
        this.out = out;
        this.externals = externals;
        this.modulePath = modulePath;
    }

    public void handleEvent(SVNEvent event, double progress) {
        /*
         * Gets the current action. An action is represented by SVNEventAction.
         * In case of an update an  action  can  be  determined  via  comparing
         * SVNEvent.getAction() and SVNEventAction.UPDATE_-like constants.
         */
        SVNEventAction action = event.getAction();
        String pathChangeType = " ";
        if (action == SVNEventAction.UPDATE_ADD) {
            /*
             * the item was added
             */
            pathChangeType = "A";
        } else if (action == SVNEventAction.UPDATE_DELETE) {
            /*
             * the item was deleted
             */
            pathChangeType = "D";
        } else if (action == SVNEventAction.UPDATE_UPDATE) {
            /*
             * Find out in details what  state the item is (after  having  been
             * updated).
             *
             * Gets  the  status  of  file/directory  item   contents.  It   is
             * SVNStatusType  who contains information on the state of an item.
             */
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
        } else if (action == SVNEventAction.UPDATE_EXTERNAL) {
            // for externals definitions
            SVNExternalInfo ext = event.getExternalInfo();
            if(ext==null) {
                // prepare for the situation where the user created their own svnkit
                File path = null;
                try {
                    path = Which.jarFile(SVNEvent.class);
                } catch (IOException e) {
                    // ignore this failure
                }
                out.println("AssertionError: appears to be using unpatched svnkit at "+ path);
            } else {
                out.println(Messages.SubversionUpdateEventHandler_FetchExternal(
                        ext.getNewURL(), ext.getNewRevision(), event.getFile()));
                //#1539 - an external inside an external needs to have the path appended 
                externals.add(new SubversionSCM.External(modulePath + "/" + event.getPath().substring(0
                		,event.getPath().length() - ext.getPath().length())
                		,ext));
            }
            return;
        } else if (action == SVNEventAction.UPDATE_COMPLETED) {
            /*
             * Updating the working copy is completed. Prints out the revision.
             */
            out.println("At revision " + event.getRevision());
            return;
        } else if (action == SVNEventAction.ADD){
            out.println("A     " + event.getPath());
            return;
        } else if (action == SVNEventAction.DELETE){
            out.println("D     " + event.getPath());
            return;
        } else if (action == SVNEventAction.LOCKED){
            out.println("L     " + event.getPath());
            return;
        } else if (action == SVNEventAction.LOCK_FAILED){
            out.println("failed to lock    " + event.getPath());
            return;
        }

        /*
         * Now getting the status of properties of an item. SVNStatusType  also
         * contains information on the properties state.
         */
        SVNStatusType propertiesStatus = event.getPropertiesStatus();
        /*
         * At first consider properties are normal (unchanged).
         */
        String propertiesChangeType = " ";
        if (propertiesStatus == SVNStatusType.CHANGED) {
            /*
             * Properties were updated.
             */
            propertiesChangeType = "U";
        } else if (propertiesStatus == SVNStatusType.CONFLICTED) {
            /*
             * Properties are in conflict with the repository.
             */
            propertiesChangeType = "C";
        } else if (propertiesStatus == SVNStatusType.MERGED) {
            /*
             * Properties that came from the repository were  merged  with  the
             * local ones.
             */
            propertiesChangeType = "G";
        }

        /*
         * Gets the status of the lock.
         */
        String lockLabel = " ";
        SVNStatusType lockType = event.getLockStatus();

        if (lockType == SVNStatusType.LOCK_UNLOCKED) {
            /*
             * The lock is broken by someone.
             */
            lockLabel = "B";
        }

        if(pathChangeType.equals(" ") && propertiesChangeType.equals(" ") && lockLabel.equals(" "))
            // nothing to display here.
            // SVNKit always seems to send one such line. 
            return;

        out.println(pathChangeType
                + propertiesChangeType
                + lockLabel
                + "       "
                + event.getPath());
    }

    public void checkCancelled() throws SVNCancelException {
        if(Thread.interrupted())
            throw new SVNCancelException();
    }
}