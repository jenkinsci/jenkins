/*
 * The MIT License
 *
 * Copyright (c) 2023, Tim Jacomb
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

package jenkins.appearance;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import hudson.util.FormApply;
import java.io.IOException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

@Extension
public class AppearanceGlobalConfiguration extends ManagementLink {

    private static final Logger LOGGER = Logger.getLogger(AppearanceGlobalConfiguration.class.getName());

    @Restricted(NoExternalUse.class)
    public static final Predicate<Descriptor> FILTER = input -> input.getCategory() instanceof AppearanceCategory;

    @Override
    public String getIconFileName() {
        if (Functions.getSortedDescriptorsForGlobalConfigByDescriptor(FILTER).isEmpty()) {
            return null;
        }

        return "symbol-brush-outline";
    }

    @Override
    public String getDisplayName() {
        return Messages.AppearanceGlobalConfiguration_DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.AppearanceGlobalConfiguration_Description();
    }

    @Override
    public String getUrlName() {
        return "appearance";
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }

    @POST
    public synchronized void doConfigure(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        boolean result = configure(req, req.getSubmittedForm());
        LOGGER.log(Level.FINE, "appearance saved: " + result);
        FormApply.success(req.getContextPath() + "/manage").generateResponse(req, rsp, null);
    }

    private boolean configure(StaplerRequest req, JSONObject json) throws Descriptor.FormException, IOException {
        Jenkins j = Jenkins.get();
        j.checkPermission(Jenkins.MANAGE);

        boolean result = true;
        for (Descriptor<?> d : Functions.getSortedDescriptorsForGlobalConfigByDescriptor(FILTER)) {
            result &= configureDescriptor(req, json, d);
        }
        j.save();

        return result;
    }

    private boolean configureDescriptor(StaplerRequest req, JSONObject json, Descriptor<?> d) throws Descriptor.FormException {
        String name = d.getJsonSafeClassName();
        JSONObject js = json.has(name) ? json.getJSONObject(name) : new JSONObject(); // if it doesn't have the property, the method returns invalid null object.
        json.putAll(js);
        return d.configure(req, js);
    }
}
