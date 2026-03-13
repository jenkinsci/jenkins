/*
 * The MIT License
 *
 * Copyright (c) 2023, CloudBees Inc, and other contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.agents;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.Util;
import hudson.model.AbstractModelObject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.model.ManagementLink;
import hudson.model.RootAction;
import hudson.model.UpdateCenter;
import hudson.slaves.Cloud;
import hudson.util.FormApply;
import hudson.util.FormValidation;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

@Restricted(NoExternalUse.class)
public class CloudSet extends AbstractModelObject implements Describable<CloudSet>, ModelObjectWithChildren, RootAction, StaplerProxy {
    private static final Logger LOGGER = Logger.getLogger(CloudSet.class.getName());

    @Override
    public Descriptor<CloudSet> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(CloudSet.class);
    }

    public Cloud getDynamic(String token) {
        return Jenkins.get().getCloud(token);
    }

    @Override
    @Restricted(NoExternalUse.class)
    public Object getTarget() {
        Jenkins.get().checkPermission(Jenkins.SYSTEM_READ);
        return this;
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
        return "cloud";
    }

    @Override
    public String getSearchUrl() {
        return "/cloud/";
    }

    @SuppressWarnings("unused")
    @Restricted(DoNotUse.class) // used by jelly
    public ManagementLink getManagementLink() {
        return ExtensionList.lookupSingleton(CloudsLink.class);
    }

    @SuppressWarnings("unused") // stapler
    @Restricted(DoNotUse.class) // stapler
    public String getCloudUrl(StaplerRequest2 request, Jenkins jenkins, Cloud cloud) {
        String context = Functions.getNearestAncestorUrl(request, jenkins);
        if (Jenkins.get().getCloud(cloud.name) != cloud) { // this cloud is not the first occurrence with this name
            return context + "/cloud/cloudByIndex/" + getClouds().indexOf(cloud) + "/";
        } else {
            return context + "/" + cloud.getUrl();
        }
    }

    /**
     * @deprecated use {@link #getCloudUrl(StaplerRequest2, Jenkins, Cloud)}
     */
    @Deprecated
    @SuppressWarnings("unused") // stapler
    @Restricted(DoNotUse.class) // stapler
    public String getCloudUrl(StaplerRequest request, Jenkins jenkins, Cloud cloud) {
        return getCloudUrl(StaplerRequest.toStaplerRequest2(request), jenkins, cloud);
    }

    @SuppressWarnings("unused") // stapler
    @Restricted(DoNotUse.class) // stapler
    public Cloud getCloudByIndex(int index) {
        return Jenkins.get().clouds.get(index);
    }

    @SuppressWarnings("unused") // stapler
    public boolean isCloudAvailable() {
        return !Cloud.all().isEmpty();
    }

    @SuppressWarnings("unused") // stapler
    public String getCloudUpdateCenterCategoryLabel() {
        return URLEncoder.encode(UpdateCenter.getCategoryDisplayName("cloud"), StandardCharsets.UTF_8);
    }

    @Override
    public ModelObjectWithContextMenu.ContextMenu doChildrenContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        ModelObjectWithContextMenu.ContextMenu m = new ModelObjectWithContextMenu.ContextMenu();
        Jenkins.get().clouds.forEach(m::add);
        return m;
    }

    public Cloud getDynamic(String name, StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        return Jenkins.get().clouds.getByName(name);
    }

    @SuppressWarnings("unused") // stapler
    @Restricted(DoNotUse.class) // stapler
    public Jenkins.CloudList getClouds() {
        return Jenkins.get().clouds;
    }

    @SuppressWarnings("unused") // stapler
    @Restricted(DoNotUse.class) // stapler
    public boolean hasClouds() {
        return !Jenkins.get().clouds.isEmpty();
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
    public synchronized void doCreate(StaplerRequest2 req, StaplerResponse2 rsp,
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
            Cloud result = (Cloud) Jenkins.XSTREAM.fromXML(xml);
            result.name = name;
            jenkins.clouds.add(result);
            // send the browser to the config page
            rsp.sendRedirect2(Functions.getNearestAncestorUrl(req, jenkins) + "/" + result.getUrl() + "configure");
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

    private void handleNewCloudPage(Descriptor<Cloud> descriptor, String name, StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        checkName(name);
        JSONObject formData = req.getSubmittedForm();
        formData.put("name", name);
        formData.remove("mode"); // Cloud descriptors won't have this field.
        req.setAttribute("instance", formData);
        req.setAttribute("descriptor", descriptor);
        req.getView(this, "_new.jelly").forward(req, rsp);
    }

    /**
     * Really creates a new agent.
     */
    @POST
    public synchronized void doDoCreate(StaplerRequest2 req, StaplerResponse2 rsp,
                                            @QueryParameter String cloudDescriptorName) throws IOException, ServletException, Descriptor.FormException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        Descriptor<Cloud> cloudDescriptor = Cloud.all().findByName(cloudDescriptorName);
        if (cloudDescriptor == null) {
            throw new Failure(String.format("No cloud type ‘%s’ is known", cloudDescriptorName));
        }
        Cloud cloud = cloudDescriptor.newInstance(req, req.getSubmittedForm());
        if (!Jenkins.get().clouds.add(cloud)) {
            LOGGER.log(Level.WARNING, () -> "Creating duplicate cloud name " + cloud.name + ". Plugin " + Jenkins.get().getPluginManager().whichPlugin(cloud.getClass()) + " should be updated to support user provided name.");
        }
        // take the user back to the cloud list top page
        rsp.sendRedirect2(".");
    }

    @POST
    public void doReorder(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        var names = req.getParameterValues("name");
        if (names == null) {
            throw new Failure("No cloud names given");
        }
        var namesList = Arrays.asList(names);
        var clouds = new ArrayList<>(Jenkins.get().clouds);
        clouds.sort(Comparator.comparingInt(c -> getIndexOf(namesList, c)));
        Jenkins.get().clouds.replaceBy(clouds);
        FormApply.success(req.getContextPath() + "/manage").generateResponse(req, rsp, null);
    }

    private static int getIndexOf(List<String> namesList, Cloud cloud) {
        var i = namesList.indexOf(cloud.name);
        return i == -1 ? Integer.MAX_VALUE : i;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CloudSet> implements StaplerProxy {

        /**
         * Auto-completion for the "copy from" field in the new cloud page.
         */
        @SuppressWarnings("unused") // stapler
        public AutoCompletionCandidates doAutoCompleteCopyNewItemFrom(@QueryParameter final String value) {
            final AutoCompletionCandidates r = new AutoCompletionCandidates();
            Jenkins.get().clouds.stream()
                    .filter(c -> c.name.startsWith(value))
                    .forEach(c -> r.add(c.name));
            return r;
        }

        @Override
        public Object getTarget() {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            return this;
        }
    }
}
