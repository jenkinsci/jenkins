/*
 * Copyright  2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package hudson.org.apache.tools.ant.taskdefs.cvslib;

import hudson.util.ForkOutputStream;
import hudson.org.apache.tools.ant.taskdefs.AbstractCvsTask;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.LogOutputStream;
import org.apache.tools.ant.taskdefs.cvslib.CvsVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.StringTokenizer;
import org.apache.tools.ant.taskdefs.ExecuteStreamHandler;

/**
 * Examines the output of cvs log and group related changes together.
 *
 * It produces an XML output representing the list of changes.
 * <PRE>
 * <FONT color=#0000ff>&lt;!-- Root element --&gt;</FONT>
 * <FONT color=#6a5acd>&lt;!ELEMENT</FONT> changelog <FONT color=#ff00ff>(entry</FONT><FONT color=#ff00ff>+</FONT><FONT color=#ff00ff>)</FONT><FONT color=#6a5acd>&gt;</FONT>
 * <FONT color=#0000ff>&lt;!-- CVS Entry --&gt;</FONT>
 * <FONT color=#6a5acd>&lt;!ELEMENT</FONT> entry <FONT color=#ff00ff>(date,author,file</FONT><FONT color=#ff00ff>+</FONT><FONT color=#ff00ff>,msg)</FONT><FONT color=#6a5acd>&gt;</FONT>
 * <FONT color=#0000ff>&lt;!-- Date of cvs entry --&gt;</FONT>
 * <FONT color=#6a5acd>&lt;!ELEMENT</FONT> date <FONT color=#ff00ff>(#PCDATA)</FONT><FONT color=#6a5acd>&gt;</FONT>
 * <FONT color=#0000ff>&lt;!-- Author of change --&gt;</FONT>
 * <FONT color=#6a5acd>&lt;!ELEMENT</FONT> author <FONT color=#ff00ff>(#PCDATA)</FONT><FONT color=#6a5acd>&gt;</FONT>
 * <FONT color=#0000ff>&lt;!-- List of files affected --&gt;</FONT>
 * <FONT color=#6a5acd>&lt;!ELEMENT</FONT> msg <FONT color=#ff00ff>(#PCDATA)</FONT><FONT color=#6a5acd>&gt;</FONT>
 * <FONT color=#0000ff>&lt;!-- File changed --&gt;</FONT>
 * <FONT color=#6a5acd>&lt;!ELEMENT</FONT> file <FONT color=#ff00ff>(name,revision,prevrevision</FONT><FONT color=#ff00ff>?</FONT><FONT color=#ff00ff>)</FONT><FONT color=#6a5acd>&gt;</FONT>
 * <FONT color=#0000ff>&lt;!-- Name of the file --&gt;</FONT>
 * <FONT color=#6a5acd>&lt;!ELEMENT</FONT> name <FONT color=#ff00ff>(#PCDATA)</FONT><FONT color=#6a5acd>&gt;</FONT>
 * <FONT color=#0000ff>&lt;!-- Revision number --&gt;</FONT>
 * <FONT color=#6a5acd>&lt;!ELEMENT</FONT> revision <FONT color=#ff00ff>(#PCDATA)</FONT><FONT color=#6a5acd>&gt;</FONT>
 * <FONT color=#0000ff>&lt;!-- Previous revision number --&gt;</FONT>
 * <FONT color=#6a5acd>&lt;!ELEMENT</FONT> prevrevision <FONT color=#ff00ff>(#PCDATA)</FONT><FONT color=#6a5acd>&gt;</FONT>
 * </PRE>
 *
 * @version $Revision$ $Date$
 * @since Ant 1.5
 * @ant.task name="cvschangelog" category="scm"
 */
public class ChangeLogTask extends AbstractCvsTask {
    /** User list */
    private File m_usersFile;

    /** User list */
    private Vector m_cvsUsers = new Vector();

    /** Input dir */
    private File m_dir;

    /** Output */
    private OutputStream m_output;

    /** The earliest date at which to start processing entries.  */
    private Date m_start;

    /** The latest date at which to stop processing entries.  */
    private Date m_stop;

    /**
     * To filter out change logs for a certain branch, this variable will be the branch name.
     * Otherwise null.
     */
    private String branch;

    /**
     * Filesets containing list of files against which the cvs log will be
     * performed. If empty then all files will in the working directory will
     * be checked.
     */
    private List<String> m_filesets = new ArrayList<String>();


    /**
     * Set the base dir for cvs.
     *
     * @param dir The new dir value
     */
    public void setDir(final File dir) {
        m_dir = dir;
    }

    public File getDir() {
        return m_dir;
    }


    /**
     * Set the output stream for the log.
     *
     * @param destfile The new destfile value
     */
    public void setDeststream(final OutputStream destfile) {
        m_output = destfile;
    }


    /**
     * Set a lookup list of user names & addresses
     *
     * @param usersFile The file containing the users info.
     */
    public void setUsersfile(final File usersFile) {
        m_usersFile = usersFile;
    }


    /**
     * Add a user to list changelog knows about.
     *
     * @param user the user
     */
    public void addUser(final CvsUser user) {
        m_cvsUsers.addElement(user);
    }


    /**
     * Set the date at which the changelog should start.
     *
     * @param start The date at which the changelog should start.
     */
    public void setStart(final Date start) {
        m_start = start;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }


    /**
     * Set the date at which the changelog should stop.
     *
     * @param stop The date at which the changelog should stop.
     */
    public void setEnd(final Date stop) {
        m_stop = stop;
    }


    /**
     * Set the number of days worth of log entries to process.
     *
     * @param days the number of days of log to process.
     */
    public void setDaysinpast(final int days) {
        final long time = System.currentTimeMillis()
             - (long) days * 24 * 60 * 60 * 1000;

        setStart(new Date(time));
    }


    /**
     * Adds a file about which cvs logs will be generated.
     *
     * @param fileName
     *      fileName relative to {@link #setDir(File)}.
     */
    public void addFile(String fileName) {
        m_filesets.add(fileName);
    }

    public void setFile(List<String> files) {
        m_filesets  = files;
    }


    // XXX crude but how else to track the parser & handler and still pass to super impl?
    private ChangeLogParser parser;
    private RedirectingStreamHandler handler;
    /**
     * Execute task
     *
     * @exception BuildException if something goes wrong executing the
     *            cvs command
     */
    public void execute() throws BuildException {
        File savedDir = m_dir; // may be altered in validate

        try {

            validate();
            final Properties userList = new Properties();

            loadUserlist(userList);

            for (Enumeration e = m_cvsUsers.elements();
                 e.hasMoreElements();) {
                final CvsUser user = (CvsUser) e.nextElement();

                user.validate();
                userList.put(user.getUserID(), user.getDisplayname());
            }


            setCommand("log");

            if (m_filesets.isEmpty() || m_filesets.size()>10) {
                // if we are going to get logs on large number of files,
                // (or if m_files is not specified at all, in which case all the files in the directory is subjec,
                // then it's worth spending little time to figure out if we can use
                // -S for speed up

                CvsVersion myCvsVersion = new CvsVersion();
                myCvsVersion.setProject(getProject());
                myCvsVersion.setTaskName("cvsversion");
                myCvsVersion.setCvsRoot(getCvsRoot());
                myCvsVersion.setCvsRsh(getCvsRsh());
                myCvsVersion.setPassfile(getPassFile());
                myCvsVersion.setDest(m_dir);
                myCvsVersion.execute();
                if (supportsCvsLogWithSOption(myCvsVersion.getClientVersion())
                 && supportsCvsLogWithSOption(myCvsVersion.getServerVersion())) {
                    addCommandArgument("-S");
                }
            }
            if (null != m_start) {
                final SimpleDateFormat outputDate =
                    new SimpleDateFormat("yyyy-MM-dd");

                // Kohsuke patch:
                // probably due to timezone difference between server/client and
                // the lack of precise specification in the protocol or something,
                // sometimes the java.net CVS server (and probably others) don't
                // always report all the changes that have happened in the given day.
                // so let's take the date range bit wider, to make sure that
                // the server sends us all the logs that we care.
                //
                // the only downside of this change is that it will increase the traffic
                // unnecessarily, but given that in Hudson we already narrow down the scope
                // by specifying files, this should be acceptable increase.

                Date safeStart = new Date(m_start.getTime()-1000L*60*60*24);

                // Kohsuke patch until here

                // We want something of the form: -d ">=YYYY-MM-dd"
                final String dateRange = ">=" + outputDate.format(safeStart);

        // Supply '-d' as a separate argument - Bug# 14397
                addCommandArgument("-d");
                addCommandArgument(dateRange);
            }

            // Check if list of files to check has been specified
            if (!m_filesets.isEmpty()) {
                addCommandArgument("--");
                for (String file : m_filesets) {
                    addCommandArgument(file);
                }
            }

            parser = new ChangeLogParser(this);

            log("Running "+getCommand()+" at "+m_dir, Project.MSG_VERBOSE);

            setDest(m_dir);
            try {
                super.execute();
            } finally {
                final String errors = handler.getErrors();

                if (null != errors && errors.length()!=0) {
                    log(errors, Project.MSG_ERR);
                }
            }

            final CVSEntry[] entrySet = parser.getEntrySetAsArray();
            final CVSEntry[] filteredEntrySet = filterEntrySet(entrySet);

            replaceAuthorIdWithName(userList, filteredEntrySet);

            writeChangeLog(filteredEntrySet);

        } finally {
            m_dir = savedDir;
        }
    }
    protected @Override ExecuteStreamHandler getExecuteStreamHandler(InputStream input) {
        return handler = new RedirectingStreamHandler(
                // stdout goes to the changelog parser,
                // but we also send this to Ant logger so that we can see it at sufficient debug level
                new ForkOutputStream(new RedirectingOutputStream(parser),
                    new LogOutputStream(this,Project.MSG_VERBOSE)),
                // stderr goes to the logger, too
                new LogOutputStream(this,Project.MSG_WARN),
                
                input);
    }

    private static final long VERSION_1_11_2 = 11102;
    private static final long MULTIPLY = 100;
    /**
     * Rip off from {@link CvsVersion#supportsCvsLogWithSOption()}
     * but we need to check both client and server.
     */
    private boolean supportsCvsLogWithSOption(String versionString) {
        if (versionString == null) {
            return false;
        }
        StringTokenizer mySt = new StringTokenizer(versionString, ".");
        long counter = MULTIPLY * MULTIPLY;
        long version = 0;
        while (mySt.hasMoreTokens()) {
            String s = mySt.nextToken();
            int startpos;
            // find the first digit char
            for (startpos = 0; startpos < s.length(); startpos++)
                if (Character.isDigit(s.charAt(startpos)))
                    break;
            // ... and up to the end of this digit set
            int i;
            for (i = startpos; i < s.length(); i++) {
                if (!Character.isDigit(s.charAt(i))) {
                    break;
                }
            }
            String s2 = s.substring(startpos, i);
            version = version + counter * Long.parseLong(s2);
            if (counter == 1) {
                break;
            }
            counter = counter / MULTIPLY;
        }
        return (version >= VERSION_1_11_2);
    }

    /**
     * Validate the parameters specified for task.
     *
     * @throws BuildException if fails validation checks
     */
    private void validate()
         throws BuildException {
        if (null == m_dir) {
            m_dir = getProject().getBaseDir();
        }
        if (null == m_output) {
            final String message = "Destfile must be set.";

            throw new BuildException(message);
        }
        if (!m_dir.exists()) {
            final String message = "Cannot find base dir "
                 + m_dir.getAbsolutePath();

            throw new BuildException(message);
        }
        if (null != m_usersFile && !m_usersFile.exists()) {
            final String message = "Cannot find user lookup list "
                 + m_usersFile.getAbsolutePath();

            throw new BuildException(message);
        }
    }

    /**
     * Load the userlist from the userList file (if specified) and add to
     * list of users.
     *
     * @param userList the file of users
     * @throws BuildException if file can not be loaded for some reason
     */
    private void loadUserlist(final Properties userList)
         throws BuildException {
        if (null != m_usersFile) {
            try {
                userList.load(new FileInputStream(m_usersFile));
            } catch (final IOException ioe) {
                throw new BuildException(ioe.toString(), ioe);
            }
        }
    }

    /**
     * Filter the specified entries according to an appropriate rule.
     *
     * @param entrySet the entry set to filter
     * @return the filtered entry set
     */
    private CVSEntry[] filterEntrySet(final CVSEntry[] entrySet) {
        log("Filtering entries",Project.MSG_VERBOSE);

        final Vector results = new Vector();

        for (int i = 0; i < entrySet.length; i++) {
            final CVSEntry cvsEntry = entrySet[i];
            final Date date = cvsEntry.getDate();
            
            if(date==null) {
                // skip dates that didn't parse.
                log("Filtering out "+cvsEntry+" because it has no date",Project.MSG_VERBOSE);
                continue;
            }

            if (null != m_start && m_start.after(date)) {
                //Skip dates that are too early
                log("Filtering out "+cvsEntry+" because it's too early compare to "+m_start,Project.MSG_VERBOSE);
                continue;
            }
            if (null != m_stop && m_stop.before(date)) {
                //Skip dates that are too late
                log("Filtering out "+cvsEntry+" because it's too late compare to "+m_stop,Project.MSG_VERBOSE);
                continue;
            }
            if (!cvsEntry.containsBranch(branch)) {
                // didn't match the branch
                log("Filtering out "+cvsEntry+" because it didn't match the branch",Project.MSG_VERBOSE);
                continue;
            }
            results.addElement(cvsEntry);
        }

        final CVSEntry[] resultArray = new CVSEntry[results.size()];

        results.copyInto(resultArray);
        return resultArray;
    }

    /**
     * replace all known author's id's with their maven specified names
     */
    private void replaceAuthorIdWithName(final Properties userList,
                                         final CVSEntry[] entrySet) {
        for (int i = 0; i < entrySet.length; i++) {

            final CVSEntry entry = entrySet[ i ];
            if (userList.containsKey(entry.getAuthor())) {
                entry.setAuthor(userList.getProperty(entry.getAuthor()));
            }
        }
    }

    /**
     * Print changelog to file specified in task.
     *
     * @param entrySet the entry set to write.
     * @throws BuildException if there is an error writing changelog.
     */
    private void writeChangeLog(final CVSEntry[] entrySet)
         throws BuildException {
        OutputStream output = null;

        try {
            output = m_output;

            final PrintWriter writer =
                new PrintWriter(new OutputStreamWriter(output, "UTF-8"));

            final ChangeLogWriter serializer = new ChangeLogWriter();

            serializer.printChangeLog(writer, entrySet);
        } catch (final UnsupportedEncodingException uee) {
            getProject().log(uee.toString(), Project.MSG_ERR);
        } catch (final IOException ioe) {
            throw new BuildException(ioe.toString(), ioe);
        } finally {
            if (null != output) {
                try {
                    output.close();
                } catch (final IOException ioe) {
                }
            }
        }
    }
}