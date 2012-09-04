package hudson.maven;

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.ArgumentListBuilder;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class SettingsProvider extends AbstractDescribableImpl<SettingsProvider> implements ExtensionPoint {

    /**
     * configure maven launcher argument list with adequate settings path
     * @param margs
     * @param project
     */
    public abstract void configure(ArgumentListBuilder margs, AbstractBuild project) throws IOException, InterruptedException;

    public static SettingsProvider parseSettingsProvider(StaplerRequest req) throws Descriptor.FormException, ServletException {
        String scm = req.getParameter("settings");
        if(scm==null)   return new DefaultSettingsProvider();

        int scmidx = Integer.parseInt(scm);
        SettingsProviderDescriptor d = SettingsProviderDescriptor.all().get(scmidx);
        return d.newInstance(req, req.getSubmittedForm().getJSONObject("settings"));
    }

}

