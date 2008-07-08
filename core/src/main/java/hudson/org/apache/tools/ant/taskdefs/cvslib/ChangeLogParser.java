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
// patched to work around http://issues.apache.org/bugzilla/show_bug.cgi?id=38583

import org.apache.tools.ant.Project;
import org.apache.commons.io.FileUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.io.IOException;

/**
 * A class used to parse the output of the CVS log command.
 *
 * @version $Revision$ $Date$
 */
class ChangeLogParser {
    //private static final int GET_ENTRY = 0;
    private static final int GET_FILE = 1;
    private static final int GET_DATE = 2;
    private static final int GET_COMMENT = 3;
    private static final int GET_REVISION = 4;
    private static final int GET_PREVIOUS_REV = 5;
    private static final int GET_SYMBOLIC_NAMES = 6;

    /**
     * input format for dates read in from cvs log.
     *
     * Some users reported that they see different formats,
     * so this is extended from original Ant version to cover different formats.
     *
     * <p>
     * KK: {@link SimpleDateFormat} is not thread safe, so make it per-instance.
     */
    private final SimpleDateFormat[] c_inputDate
        = new SimpleDateFormat[]{
            new SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z"),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z"),
            new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
        };

    {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        for (SimpleDateFormat df : c_inputDate) {
            df.setTimeZone(utc);
        }
    }

    //The following is data used while processing stdout of CVS command
    private String m_file;
    private String m_fullName;
    private String m_date;
    private String m_author;
    private String m_comment;
    private String m_revision;
    private String m_previousRevision;
    /**
     * All branches available on the current file.
     * Keyed by branch revision prefix (like "1.2.3." if files in the branch have revision numbers like
     * "1.2.3.4") and the value is the branch name.
     */
    private final Map<String,String> branches = new HashMap<String,String>();
    /**
     * True if the log record indicates deletion;
     */
    private boolean m_dead;

    private int m_status = GET_FILE;

    /** rcs entries */
    private final Hashtable<String,CVSEntry> m_entries = new Hashtable<String,CVSEntry>();

    private final ChangeLogTask owner;

    public ChangeLogParser(ChangeLogTask owner) {
        this.owner = owner;
    }

    /**
     * Get a list of rcs entries as an array.
     *
     * @return a list of rcs entries as an array
     */
    CVSEntry[] getEntrySetAsArray() {
        final CVSEntry[] array = new CVSEntry[ m_entries.size() ];
        Enumeration e = m_entries.elements();
        int i = 0;
        while (e.hasMoreElements()) {
            array[i++] = (CVSEntry) e.nextElement();
        }
        return array;
    }

    private boolean dead = false;
    private String previousLine = null;
    
    /**
     * Receive notification about the process writing
     * to standard output.
     */
    public void stdout(final String line) {
        if(dead)
            return;
        try {
            switch(m_status) {
                case GET_FILE:
                    // make sure attributes are reset when
                    // working on a 'new' file.
                    reset();
                    processFile(line);
                    break;
                case GET_SYMBOLIC_NAMES:
                    processSymbolicName(line);
                    break;

                case GET_REVISION:
                    processRevision(line);
                    break;

                case GET_DATE:
                    processDate(line);
                    break;

                case GET_COMMENT:
                    processComment(line);
                    break;

                case GET_PREVIOUS_REV:
                    processGetPreviousRevision(line);
                    break;
            }
        } catch (Exception e) {
            // we don't know how to handle the input any more. don't accept any more input
            dead = true;
        }
    }

    /**
     * Process a line while in "GET_COMMENT" state.
     *
     * @param line the line
     */
    private void processComment(final String line) {
        final String lineSeparator = System.getProperty("line.separator");
        if (line.startsWith("======")) {
            //We have ended changelog for that particular file
            //so we can save it
            final int end
                = m_comment.length() - lineSeparator.length(); //was -1
            m_comment = m_comment.substring(0, end);
            saveEntry();
            m_status = GET_FILE;
        } else if (null != previousLine && previousLine.startsWith("----------------------------")) {
            if (line.startsWith("revision")) {
                final int end
                    = m_comment.length() - lineSeparator.length(); //was -1
                m_comment = m_comment.substring(0, end);
                m_status = GET_PREVIOUS_REV;
                
                processGetPreviousRevision(line);
            } else {
                m_comment += previousLine + lineSeparator + line + lineSeparator;
            }
            
            previousLine = null;
        } else if (line.startsWith("----------------------------")) {
            if (null != previousLine) {
                m_comment += previousLine + lineSeparator;
            }
            previousLine = line;
        } else {
            m_comment += line + lineSeparator;
        }
    }

    /**
     * Process a line while in "GET_FILE" state.
     *
     * @param line the line
     */
    private void processFile(final String line) {
        if (line.startsWith("Working file:")) {
            m_file = line.substring(14, line.length());

            File repo = new File(new File(owner.getDir(), m_file).getParentFile(), "CVS/Repository");
            try {
                String module = FileUtils.readFileToString(repo, null);// not sure what encoding CVS uses.
                String simpleName = m_file.substring(m_file.lastIndexOf('/')+1);
                m_fullName = '/'+module.trim()+'/'+simpleName;
            } catch (IOException e) {
                // failed to read
                LOGGER.log(Level.WARNING, "Failed to read CVS/Repository at "+repo,e);
                m_fullName = null;
            }

            m_status = GET_SYMBOLIC_NAMES;
        }
    }

    /**
     * Obtains the revision name list
     */
    private void processSymbolicName(String line) {
        if (line.startsWith("\t")) {
            line = line.trim();
            int idx = line.lastIndexOf(':');
            if(idx<0) {
                // ???
                return;
            }

            String symbol = line.substring(0,idx);
            Matcher m = DOT_PATTERN.matcher(line.substring(idx + 2));
            if(!m.matches())
                return; // not a branch name

            branches.put(m.group(1)+m.group(3)+'.',symbol);
        } else
        if (line.startsWith("keyword substitution:")) {
            m_status = GET_REVISION;
        }
    }

    private static final Pattern DOT_PATTERN = Pattern.compile("(([0-9]+\\.)+)0\\.([0-9]+)");

    /**
     * Process a line while in "REVISION" state.
     *
     * @param line the line
     */
    private void processRevision(final String line) {
        if (line.startsWith("revision")) {
            m_revision = line.substring(9);
            m_status = GET_DATE;
        } else if (line.startsWith("======")) {
            //There was no revisions in this changelog
            //entry so lets move unto next file
            m_status = GET_FILE;
        }
    }

    /**
     * Process a line while in "DATE" state.
     *
     * @param line the line
     */
    private void processDate(final String line) {
        if (line.startsWith("date:")) {
            int idx = line.indexOf(";");
            m_date = line.substring(6, idx);
            String lineData = line.substring(idx + 1);
            m_author = lineData.substring(10, lineData.indexOf(";"));

            m_status = GET_COMMENT;

            m_dead = lineData.indexOf("state: dead;")!=-1;

            //Reset comment to empty here as we can accumulate multiple lines
            //in the processComment method
            m_comment = "";
        }
    }

    /**
     * Process a line while in "GET_PREVIOUS_REVISION" state.
     *
     * @param line the line
     */
    private void processGetPreviousRevision(final String line) {
        if (!line.startsWith("revision")) {
            throw new IllegalStateException("Unexpected line from CVS: "
                + line);
        }
        m_previousRevision = line.substring(9);

        saveEntry();

        m_revision = m_previousRevision;
        m_status = GET_DATE;
    }

    /**
     * Utility method that saves the current entry.
     */
    private void saveEntry() {
        final String entryKey = m_date + m_author + m_comment;
        CVSEntry entry;
        if (!m_entries.containsKey(entryKey)) {
            entry = new CVSEntry(parseDate(m_date), m_author, m_comment);
            m_entries.put(entryKey, entry);
        } else {
            entry = m_entries.get(entryKey);
        }

        String branch = findBranch(m_revision);

        owner.log("Recorded a change: "+m_date+','+m_author+','+m_revision+"(branch="+branch+"),"+m_comment,Project.MSG_VERBOSE);

        entry.addFile(m_file, m_fullName, m_revision, m_previousRevision, branch, m_dead);
    }

    /**
     * Finds the branch name that matches the revision, or null if not found.
     */
    private String findBranch(String revision) {
        if(revision==null)  return null; // defensive check
        for (Entry<String,String> e : branches.entrySet()) {
            if(revision.startsWith(e.getKey()) && revision.substring(e.getKey().length()).indexOf('.')==-1)
                return e.getValue();
        }
        return null;
    }

    /**
     * Parse date out from expected format.
     *
     * @param date the string holding dat
     * @return the date object or null if unknown date format
     */
    private Date parseDate(String date) {
        for (SimpleDateFormat df : c_inputDate) {
            try {
                return df.parse(date);
            } catch (ParseException e) {
                // try next if one fails
            }
        }

        // nothing worked
        owner.log("Failed to parse "+date+"\n", Project.MSG_ERR);
        //final String message = REZ.getString( "changelog.bat-date.error", date );
        //getContext().error( message );
        return null;
    }

    /**
     * reset all internal attributes except status.
     */
    private void reset() {
        m_file = null;
        m_fullName = null;
        m_date = null;
        m_author = null;
        m_comment = null;
        m_revision = null;
        m_previousRevision = null;
        m_dead = false;
        branches.clear();
    }

    private static final Logger LOGGER = Logger.getLogger(ChangeLogParser.class.getName());
}
