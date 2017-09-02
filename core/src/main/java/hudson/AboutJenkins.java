package hudson;

import hudson.model.ManagementLink;
import java.net.URL;

import jenkins.bootstrap.Bootstrap;
import jenkins.bootstrap.OverrideJournal;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.inject.Inject;

/**
 * Show "About Jenkins" link.
 * 
 * @author Kohsuke Kawaguchi
 */
@Extension @Symbol("about")
public class AboutJenkins extends ManagementLink {
    @Inject
    private Bootstrap bootstrap;

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

    public OverrideJournal getOverrideJournal() {
        return bootstrap.getOverrides();
    }
}
