/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
import hudson.scm.SubversionChangeLogSet.LogEntry;
import hudson.scm.SubversionChangeLogSet.Path;
import hudson.util.Digester2;
import hudson.util.IOException2;
import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * {@link ChangeLogParser} for Subversion.
 *
 * @author Kohsuke Kawaguchi
 */
public class SubversionChangeLogParser extends ChangeLogParser {
    public SubversionChangeLogSet parse(AbstractBuild build, File changelogFile) throws IOException, SAXException {
        // http://svn.collab.net/repos/svn/trunk/subversion/svn/schema/

        Digester digester = new Digester2();
        ArrayList<LogEntry> r = new ArrayList<LogEntry>();
        digester.push(r);

        digester.addObjectCreate("*/logentry", LogEntry.class);
        digester.addSetProperties("*/logentry");
        digester.addBeanPropertySetter("*/logentry/author","user");
        digester.addBeanPropertySetter("*/logentry/date");
        digester.addBeanPropertySetter("*/logentry/msg");
        digester.addSetNext("*/logentry","add");

        digester.addObjectCreate("*/logentry/paths/path", Path.class);
        digester.addSetProperties("*/logentry/paths/path");
        digester.addBeanPropertySetter("*/logentry/paths/path","value");
        digester.addSetNext("*/logentry/paths/path","addPath");

        try {
            digester.parse(changelogFile);
        } catch (IOException e) {
            throw new IOException2("Failed to parse "+changelogFile,e);
        } catch (SAXException e) {
            throw new IOException2("Failed to parse "+changelogFile,e);
        }

        return new SubversionChangeLogSet(build,r);
    }

}
