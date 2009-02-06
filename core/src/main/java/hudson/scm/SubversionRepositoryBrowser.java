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

import java.io.IOException;
import java.net.URL;

/**
 * {@link RepositoryBrowser} for Subversion.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SubversionRepositoryBrowser extends RepositoryBrowser<SubversionChangeLogSet.LogEntry> {
    /**
     * Determines the link to the diff between the version
     * in the specified revision of {@link SubversionChangeLogSet.Path} to its previous version.
     *
     * @return
     *      null if the browser doesn't have any URL for diff.
     */
    public abstract URL getDiffLink(SubversionChangeLogSet.Path path) throws IOException;

    /**
     * Determines the link to a single file under Subversion.
     * This page should display all the past revisions of this file, etc.
     *
     * @return
     *      null if the browser doesn't have any suitable URL.
     */
    public abstract URL getFileLink(SubversionChangeLogSet.Path path) throws IOException;

    private static final long serialVersionUID = 1L;
}
