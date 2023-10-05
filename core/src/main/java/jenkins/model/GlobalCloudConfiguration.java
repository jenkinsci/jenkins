package jenkins.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.RestrictedSince;
import hudson.model.RootAction;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Redirects from /configureClouds to /cloud/.
 * Previously was the form for clouds.
 * @deprecated Replaced by {@link jenkins.agents.CloudsLink} and {@link jenkins.agents.CloudSet}.
 */
@Extension
@Symbol("cloud")
@Restricted(NoExternalUse.class)
@RestrictedSince("2.205")
@Deprecated
public class GlobalCloudConfiguration implements RootAction {

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return Messages.GlobalCloudConfiguration_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "configureClouds";
    }
}
