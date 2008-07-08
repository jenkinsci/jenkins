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

import org.apache.commons.lang.time.FastDateFormat;

import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.TimeZone;

/**
 * Class used to generate an XML changelog.
 *
 * @version $Revision$ $Date$
 */
class ChangeLogWriter {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /** output format for dates written to xml file */
    private static final FastDateFormat c_outputDate
        = FastDateFormat.getInstance("yyyy-MM-dd", UTC);
    /** output format for times written to xml file */
    private static final FastDateFormat c_outputTime
        = FastDateFormat.getInstance("HH:mm", UTC);

    /**
     * Print out the specified entries.
     *
     * @param output writer to which to send output.
     * @param entries the entries to be written.
     */
    public void printChangeLog(final PrintWriter output,
                               final CVSEntry[] entries) {
        output.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        output.println("<changelog>");
        for (int i = 0; i < entries.length; i++) {
            final CVSEntry entry = entries[i];

            printEntry(output, entry);
        }
        output.println("</changelog>");
        output.flush();
        output.close();
    }


    /**
     * Print out an individual entry in changelog.
     *
     * @param entry the entry to print
     * @param output writer to which to send output.
     */
    private void printEntry(final PrintWriter output, final CVSEntry entry) {
        output.println("\t<entry>");
        output.println("\t\t<date>" + c_outputDate.format(entry.getDate())
            + "</date>");
        output.println("\t\t<time>" + c_outputTime.format(entry.getDate())
            + "</time>");
        output.println("\t\t<author><![CDATA[" + entry.getAuthor()
            + "]]></author>");

        final Enumeration enumeration = entry.getFiles().elements();

        while (enumeration.hasMoreElements()) {
            final RCSFile file = (RCSFile) enumeration.nextElement();

            output.println("\t\t<file>");
            output.println("\t\t\t<name>" + file.getName() + "</name>");
            if(file.getFullName()!=null)
                output.println("\t\t\t<fullName>" + file.getFullName() + "</fullName>");
            output.println("\t\t\t<revision>" + file.getRevision()
                + "</revision>");

            final String previousRevision = file.getPreviousRevision();

            if (previousRevision != null) {
                output.println("\t\t\t<prevrevision>" + previousRevision
                    + "</prevrevision>");
            }

            if(file.isDead())
                output.println("\t\t\t<dead />");

            output.println("\t\t</file>");
        }
        output.println("\t\t<msg><![CDATA[" + entry.getComment() + "]]></msg>");
        output.println("\t</entry>");
    }
}

