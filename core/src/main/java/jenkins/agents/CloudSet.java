package jenkins.agents;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractModelObject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.model.RootAction;
import hudson.slaves.Cloud;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

@ExportedBean
@Extension
public class CloudSet extends AbstractModelObject implements Describable<CloudSet>, StaplerFallback, ModelObjectWithChildren, RootAction {
    @Override
    public Descriptor<CloudSet> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(CloudSet.class);
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return Messages.CloudSet_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "cloudSet";
    }

    @Override
    public String getSearchUrl() {
        return "/cloudSet/";
    }

    @Exported(name = "cloud", inline = true)
    public Jenkins.CloudList get_all() {
        return Jenkins.get().clouds;
    }

    @Override
    public ModelObjectWithContextMenu.ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        ModelObjectWithContextMenu.ContextMenu m = new ModelObjectWithContextMenu.ContextMenu();
        get_all().stream().forEach(c -> m.add(c));
        return m;
    }

    @Override
    public Object getStaplerFallback() {
        return Jenkins.get();
    }

    /**
     * Gets all the agent names.
     */
    @SuppressWarnings("unused") // stapler
    public List<String> get_cloudNames() {
        return Jenkins.get().clouds
                .stream()
                .map(c -> c.name)
                .collect(Collectors.toList());
    }

    /**
     * Makes sure that the given name is good as an agent name.
     * @return trimmed name if valid; throws ParseException if not
     */
    public String checkName(String name) throws Failure {
        if (name == null)
            throw new Failure("Query parameter 'name' is required");

        name = name.trim();
        Jenkins.checkGoodName(name);

        if (Jenkins.get().getCloud(name) != null)
            throw new Failure(Messages.CloudSet_CloudAlreadyExists(name));

        // looks good
        return name;
    }

    @SuppressWarnings("unused") // stapler
    public FormValidation doCheckName(@QueryParameter String value) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (Util.fixEmpty(value) == null) {
            return FormValidation.ok();
        }
        try {
            checkName(value);
            return FormValidation.ok();
        } catch (Failure e) {
            return FormValidation.error(e.getMessage());
        }
    }

    /**
     * First check point in creating a new cloud.
     */
    @RequirePOST
    public synchronized void doCreateItem(StaplerRequest req, StaplerResponse rsp,
                                          @QueryParameter String name, @QueryParameter String mode,
                                          @QueryParameter String from) throws IOException, ServletException, Descriptor.FormException {
        final Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Jenkins.ADMINISTER);

        if (mode != null && mode.equals("copy")) {
            name = checkName(name);

            Cloud src = jenkins.getCloud(from);
            if (src == null) {
                if (Util.fixEmpty(from) == null) {
                    throw new Failure(Messages.CloudSet_SpecifyCloudToCopy());
                } else {
                    throw new Failure(Messages.CloudSet_NoSuchCloud(from));
                }
            }

            // copy through XStream
            String xml = Jenkins.XSTREAM.toXML(src);
            // Not great, but cloud name is final
            xml = xml.replace("<name>" + src.name + "</name>", "<name>" + name + "</name>");
            Cloud result = (Cloud) Jenkins.XSTREAM.fromXML(xml);
            jenkins.clouds.add(result);
            // send the browser to the config page
            rsp.sendRedirect2(result.getUrl() + "configure");
        } else {
            // proceed to step 2
            if (mode == null) {
                throw new Failure("No mode given");
            }

            Descriptor<Cloud> d = Cloud.all().findByName(mode);
            if (d == null) {
                throw new Failure("No node type ‘" + mode + "’ is known");
            }
            handleNewCloudPage(d, name, req, rsp);
        }
    }

    private void handleNewCloudPage(Descriptor<Cloud> descriptor, String name, StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        checkName(name);
        JSONObject formData = req.getSubmittedForm();
        formData.put("name", name);
        formData.remove("mode"); // Cloud descriptors won't have this field.
        Cloud instance = descriptor.newInstance(req, formData); // Not great but that's the best I have so far to pass the given name.
        req.setAttribute("instance", instance);
        req.getView(this, "_new.jelly").forward(req, rsp);
    }

    /**
     * Really creates a new agent.
     */
    @POST
    public synchronized void doDoCreateItem(StaplerRequest req, StaplerResponse rsp,
                                            @QueryParameter("_.name") String name,
                                            @QueryParameter String type) throws IOException, ServletException, Descriptor.FormException {
        final Jenkins app = Jenkins.get();
        app.checkPermission(Jenkins.ADMINISTER);
        String fixedName = Util.fixEmptyAndTrim(name);
        checkName(fixedName);

        JSONObject formData = req.getSubmittedForm();
        formData.put("_.name", fixedName);

        Cloud result = Cloud.all().find(type).newInstance(req, formData);
        app.clouds.add(result);

        // take the user back to the cloud list top page
        rsp.sendRedirect2(".");
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CloudSet> {
        /**
         * Auto-completion for the "copy from" field in the new cloud page.
         */
        public AutoCompletionCandidates doAutoCompleteCopyNewItemFrom(@QueryParameter final String value) {
            final AutoCompletionCandidates r = new AutoCompletionCandidates();
            Jenkins.get().clouds.stream()
                    .filter(c -> c.name.startsWith(value))
                    .forEach(c -> r.add(c.name));
            return r;
        }
    }
}
