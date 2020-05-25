package hudson;

import com.google.common.collect.Sets;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import java.net.URL;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Show "About Jenkins" link.
 * 
 * @author Kohsuke Kawaguchi
 */
@Extension @Symbol("about")
public class AboutJenkins extends ManagementLink {
    @Override
    public String getIconFileName() {
        return "help.png";
    }

    @Override
    public String getUrlName() {
        return "about";
    }

    public String getDisplayName() {
        return Messages.AboutJenkins_DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.AboutJenkins_Description();
    }

    @Restricted(NoExternalUse.class)
    public URL getLicensesURL() {
        return AboutJenkins.class.getResource("/META-INF/licenses.xml");
    }

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Sets.newHashSet(Jenkins.MANAGE, Jenkins.SYSTEM_READ);
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.STATUS;
    }
}
