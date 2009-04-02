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
package hudson.maven.reporters;

import hudson.model.Action;
import org.apache.maven.reporting.MavenReport;

import java.util.List;
import java.util.ArrayList;
import java.io.Serializable;

/**
 * {@link Action} to display links to the generated {@link MavenReport Maven reports}.
 * @author Kohsuke Kawaguchi
 */
public final class ReportAction implements Action, Serializable {

    private final List<Entry> entries = new ArrayList<Entry>();

    public static final class Entry {
        /**
         * Relative path to the top of the report withtin the project reporting directory.
         */
        public final String path;
        public final String title;

        public Entry(String path, String title) {
            this.path = path;
            this.title = title;
        }
    }

    public ReportAction() {
    }

    protected void add(Entry e) {
        entries.add(e);
    }

    public String getIconFileName() {
        // TODO
        return "n/a.gif";
    }

    public String getDisplayName() {
        return Messages.ReportAction_DisplayName();
    }

    public String getUrlName() {
        return "mavenReports";
    }

    private static final long serialVersionUID = 1L;
}
