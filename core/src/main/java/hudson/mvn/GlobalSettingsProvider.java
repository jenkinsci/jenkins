package hudson.mvn;

import hudson.ExtensionPoint;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author Dominik Bartholdi (imod)
 */
public abstract class GlobalSettingsProvider extends AbstractDescribableImpl<GlobalSettingsProvider> implements ExtensionPoint {

    /**
     * configure maven launcher argument list with adequate settings path
     * @param margs
     * @param project
     */
    public abstract void configure(ArgumentListBuilder margs, AbstractBuild project) throws IOException, InterruptedException;

    public static GlobalSettingsProvider parseSettingsProvider(StaplerRequest req) throws Descriptor.FormException, ServletException {
        String scm = req.getParameter("globalSettings");
        if(scm==null)   return new DefaultGlobalSettingsProvider();

        int scmidx = Integer.parseInt(scm);
        GlobalSettingsProviderDescriptor d = GlobalSettingsProviderDescriptor.all().get(scmidx);
        return d.newInstance(req, req.getSubmittedForm().getJSONObject("settings"));
    }

}

