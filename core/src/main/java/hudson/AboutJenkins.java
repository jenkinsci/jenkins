package hudson;

import java.net.URL;
import java.util.Collection;

import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerOverridable;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import jenkins.model.Jenkins;

/**
 * Show "About Jenkins" link.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension @Symbol("about")
public class AboutJenkins extends ManagementLink implements StaplerOverridable {
    @Override
    public String getIconFileName() {
        return "symbol-jenkins";
    }

    @Override
    public String getUrlName() {
        return "about";
    }

    @Override
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
    public Permission getRequiredPermission() {
        return Jenkins.READ;
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.STATUS;
    }

    @Override
    public Collection<AboutPageStablerOverride> getOverrides() {
        return AboutPageStablerOverride.all();
    }
}
