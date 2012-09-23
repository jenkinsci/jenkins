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
public abstract class SettingsProvider extends AbstractDescribableImpl<SettingsProvider> implements ExtensionPoint {

    /**
     * configure maven launcher argument list with adequate settings path
     * @param margs
     * @param project
     */
    public abstract void configure(ArgumentListBuilder margs, AbstractBuild project) throws IOException, InterruptedException;

    public static SettingsProvider parseSettingsProvider(StaplerRequest req) throws Descriptor.FormException, ServletException {
        String scm = req.getParameter("settings");
        if(scm==null) {
            return new DefaultSettingsProvider();
        }
        return req.bindJSON(SettingsProvider.class, req.getSubmittedForm().getJSONObject("settings"));
    }

}

