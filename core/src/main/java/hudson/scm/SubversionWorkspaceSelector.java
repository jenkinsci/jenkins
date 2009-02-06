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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNAdminAreaFactorySelector;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea14;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * {@link ISVNAdminAreaFactorySelector} that uses 1.4 compatible workspace for new check out,
 * but still supports 1.5 workspace, if asked to work with it.
 *
 * <p>
 * Since there are many tools out there that still don't support Subversion 1.5 (including
 * all the major Unix distributions that haven't bundled Subversion 1.5), using 1.4 as the
 * default would reduce the likelihood of the user running into "this SVN client can't work
 * with this workspace version..." problem when using other SVN tools.
 *
 * <p>
 * The primary scenario of this is the use of command-line SVN client, either from shell
 * script, Ant, or Maven.
 *
 * @author Kohsuke Kawaguchi
 */
public class SubversionWorkspaceSelector implements ISVNAdminAreaFactorySelector {
    public SubversionWorkspaceSelector() {
        // don't upgrade the workspace.
        SVNAdminAreaFactory.setUpgradeEnabled(false);
    }

    @SuppressWarnings({"cast", "unchecked"})
    public Collection getEnabledFactories(File path, Collection factories, boolean writeAccess) throws SVNException {
        if(!writeAccess)    // for reading, use all our available factories
            return factories;

        // for writing, use 1.4
        Collection<SVNAdminAreaFactory> enabledFactories = new ArrayList<SVNAdminAreaFactory>();
        for (SVNAdminAreaFactory factory : (Collection<SVNAdminAreaFactory>)factories)
            if (factory.getSupportedVersion() == SVNAdminArea14.WC_FORMAT)
                enabledFactories.add(factory);

        return enabledFactories;
    }
}
