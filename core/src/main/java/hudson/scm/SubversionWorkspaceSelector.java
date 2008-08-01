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
