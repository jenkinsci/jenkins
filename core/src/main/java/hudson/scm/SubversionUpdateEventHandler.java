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
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import hudson.model.TaskListener;

import java.io.PrintStream;

/**
 * Just prints out the progress of svn update/checkout operation in a way similar to
 * the svn CLI.
 */
final class SubversionUpdateEventHandler implements ISVNEventHandler {

    private final PrintStream out;

    public SubversionUpdateEventHandler(TaskListener listener) {
        this.out = listener.getLogger();
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
            /*for externals definitions*/
            out.println("Fetching external item into '"
                    + event.getFile().getAbsolutePath() + "'");
            out.println("External at revision " + event.getRevision());
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

        if(pathChangeType.equals(" ") && propertiesChangeType.equals(" ") && lockLabel.equals(" ") && event.getPath().equals(""))
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
        if(Thread.currentThread().isInterrupted())
            throw new SVNCancelException();
    }
}