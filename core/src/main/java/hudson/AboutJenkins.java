package hudson;

import hudson.model.ManagementLink;
import java.net.URL;

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

}
