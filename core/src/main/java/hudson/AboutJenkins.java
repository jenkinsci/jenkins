package hudson;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import java.net.URL;
import java.util.List;
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

    public static AboutPageDecorator getAboutPageDecorator() {
        return AboutPageDecorator.first();
    }

    public static List<AboutPageDecorator> getAboutPageDecorators() {
        return AboutPageDecorator.all();
    }
}
