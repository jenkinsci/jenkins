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

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.scm.ChangeLogSet.Entry;
import java.io.File;
import java.io.IOException;
import org.xml.sax.SAXException;

/**
 * Encapsulates the file format of the changelog.
 *
 * Instances should be stateless, but
 * persisted as a part of build.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ChangeLogParser {

    /**
     * @since 1.568
     */
    public ChangeLogSet<? extends Entry> parse(Run build, RepositoryBrowser<?> browser, File changelogFile) throws IOException, SAXException {
        if (build instanceof AbstractBuild && Util.isOverridden(ChangeLogParser.class, getClass(), "parse", AbstractBuild.class, File.class)) {
            return parse((AbstractBuild) build, changelogFile);
        } else {
            throw new AbstractMethodError("You must override the newer overload of parse");
        }
    }

    @Deprecated
    public ChangeLogSet<? extends Entry> parse(AbstractBuild build, File changelogFile) throws IOException, SAXException {
        return parse((Run) build, build.getProject().getScm().getEffectiveBrowser(), changelogFile);
    }
}
