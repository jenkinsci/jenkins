/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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

package jenkins.tools;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.RestrictedSince;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import hudson.util.FormApply;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

@Extension(ordinal = Integer.MAX_VALUE - 220)
@Restricted(NoExternalUse.class)
public class GlobalToolConfiguration extends ManagementLink {

    @Override
    public String getIconFileName() {
        return "symbol-hammer";
    }

    @Override
    public String getDisplayName() {
        return jenkins.management.Messages.ConfigureTools_DisplayName();
    }

    @Override
    public String getDescription() {
        return jenkins.management.Messages.ConfigureTools_Description();
    }

    @Override
    public String getUrlName() {
        return "configureTools";
    }

    @Override
    public Permission getRequiredPermission() {
        return Jenkins.SYSTEM_READ;
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }

    @POST
    public synchronized void doConfigure(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        boolean result = configure(req, req.getSubmittedForm());
        LOGGER.log(Level.FINE, "tools saved: " + result);
        FormApply.success(req.getContextPath() + "/manage").generateResponse(req, rsp, null);
    }

    private boolean configure(StaplerRequest2 req, JSONObject json) throws Descriptor.FormException, IOException {
        Jenkins j = Jenkins.get();
        j.checkPermission(Jenkins.ADMINISTER);

        boolean result = true;
        for (Descriptor<?> d : Functions.getSortedDescriptorsForGlobalConfigByDescriptor(FILTER)) {
            result &= configureDescriptor(req, json, d);
        }
        j.save();

        return result;
    }

    private boolean configureDescriptor(StaplerRequest2 req, JSONObject json, Descriptor<?> d) throws Descriptor.FormException {
        String name = d.getJsonSafeClassName();
        JSONObject js = json.has(name) ? json.getJSONObject(name) : new JSONObject(); // if it doesn't have the property, the method returns invalid null object.
        json.putAll(js);
        return d.configure(req, js);
    }

    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.301")
    public static final Predicate<Descriptor> FILTER = input -> input.getCategory() instanceof ToolConfigurationCategory;

    private static final Logger LOGGER = Logger.getLogger(GlobalToolConfiguration.class.getName());
}
