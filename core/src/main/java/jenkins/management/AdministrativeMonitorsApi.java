package jenkins.management;

import hudson.Extension;
import hudson.model.PageDecorator;
import hudson.model.RootAction;
import jakarta.servlet.ServletException;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.GET;

@Extension
@Restricted(NoExternalUse.class)
public class AdministrativeMonitorsApi implements RootAction {
    @GET
    public void doNonSecurityPopupContent(StaplerRequest2 req, StaplerResponse2 resp) throws IOException, ServletException {
        AdministrativeMonitorsApiData viewData = new AdministrativeMonitorsApiData(getDecorator().getNonSecurityAdministrativeMonitors());
        req.getView(viewData, "monitorsList.jelly").forward(req, resp);
    }

    @GET
    public void doSecurityPopupContent(StaplerRequest2 req, StaplerResponse2 resp) throws IOException, ServletException {
        AdministrativeMonitorsApiData viewData = new AdministrativeMonitorsApiData(getDecorator().getSecurityAdministrativeMonitors());
        req.getView(viewData, "monitorsList.jelly").forward(req, resp);
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return "administrativeMonitorsApi";
    }

    private AdministrativeMonitorsDecorator getDecorator() {
        return Jenkins.get()
                .getExtensionList(PageDecorator.class)
                .get(AdministrativeMonitorsDecorator.class);
    }
}
