package jenkins.management;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import hudson.model.Api;
import hudson.model.PageDecorator;
import hudson.model.RootAction;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.verb.GET;

@Extension
@ExportedBean
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
        return "administrative-monitors";
    }

    private AdministrativeMonitorsDecorator getDecorator() {
        return Jenkins.get()
                .getExtensionList(PageDecorator.class)
                .get(AdministrativeMonitorsDecorator.class);
    }

    @Exported
    public Collection<AdministrativeMonitor> getActiveAdministrativeMonitors() {
        return ExtensionList.lookupSingleton(AdministrativeMonitorsDecorator.class).getAllActiveAdministrativeMonitors();
    }

    @Exported
    public Collection<AdministrativeMonitor> getAdministrativeMonitors() {
        return ExtensionList.lookup(AdministrativeMonitor.class);
    }

    public Api getApi() {
        // Implement a permission check so failure to authenticate doesn't look like everything's OK when accessing /api.
        // This check needs to match AdministrativeMonitorsDecorator#getMonitorsToDisplay.
        Jenkins.get().checkPermission(Jenkins.SYSTEM_READ);
        return new Api(this);
    }
}
