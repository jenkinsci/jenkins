/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

package hudson.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.Functions;
import hudson.RestrictedSince;
import hudson.markup.MarkupFormatter;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.ManagementLink;
import hudson.util.FormApply;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import jenkins.util.ServerTcpPort;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

/**
 * Security configuration.
 *
 * For historical reasons, most of the actual configuration values are stored in {@link Jenkins}.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal = Integer.MAX_VALUE - 210) @Symbol("securityConfig")
public class GlobalSecurityConfiguration extends ManagementLink implements Describable<GlobalSecurityConfiguration> {

    private static final Logger LOGGER = Logger.getLogger(GlobalSecurityConfiguration.class.getName());

    public SecurityRealm getSecurityRealm() {
        return Jenkins.get().getSecurityRealm();
    }

    public AuthorizationStrategy getAuthorizationStrategy() {
        return Jenkins.get().getAuthorizationStrategy();
    }

    public MarkupFormatter getMarkupFormatter() {
        return Jenkins.get().getMarkupFormatter();
    }

    public int getSlaveAgentPort() {
        return Jenkins.get().getSlaveAgentPort();
    }

    /**
     * @since 2.24
     * @return true if the inbound agent port is enforced on this instance.
     */
    @Restricted(NoExternalUse.class)
    public boolean isSlaveAgentPortEnforced() {
        return Jenkins.get().isSlaveAgentPortEnforced();
    }

    public boolean isDisableRememberMe() {
        return Jenkins.get().isDisableRememberMe();
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.SECURITY;
    }

    @POST
    public synchronized void doConfigure(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, FormException {
        // for compatibility reasons, the actual value is stored in Jenkins
        JSONObject json = req.getSubmittedForm();
        BulkChange bc = new BulkChange(Jenkins.get());
        try {
            boolean result = configure(req, json);
            LOGGER.log(Level.FINE, "security saved: " + result);
            Jenkins.get().save();
            FormApply.success(req.getContextPath() + "/manage").generateResponse(req, rsp, null);
        } catch (JSONException x) {
            LOGGER.warning(() -> "Bad JSON:\n" + json.toString(2));
            throw x;
        } finally {
            bc.commit();
        }
    }

    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        // for compatibility reasons, the actual value is stored in Jenkins
        Jenkins j = Jenkins.get();
        j.checkPermission(Jenkins.ADMINISTER);

        j.setDisableRememberMe(json.optBoolean("disableRememberMe", false));
        // TODO probably clearer to configure such things with @DataBoundSetter
        j.setSecurityRealm(Descriptor.bindJSON(req, SecurityRealm.class, json.getJSONObject("securityRealm")));
        j.setAuthorizationStrategy(Descriptor.bindJSON(req, AuthorizationStrategy.class, json.getJSONObject("authorizationStrategy")));

        if (json.has("markupFormatter")) {
            j.setMarkupFormatter(req.bindJSON(MarkupFormatter.class, json.getJSONObject("markupFormatter")));
        } else {
            j.setMarkupFormatter(null);
        }

        // Agent settings
        if (!isSlaveAgentPortEnforced()) {
            try {
                j.setSlaveAgentPort(new ServerTcpPort(json.getJSONObject("slaveAgentPort")).getPort());
            } catch (IOException e) {
                throw new FormException(e, "slaveAgentPortType");
            }
        }

        // persist all the additional security configs
        boolean result = true;
        for (Descriptor<?> d : Functions.getSortedDescriptorsForGlobalConfigByDescriptor(FILTER)) {
            result &= configureDescriptor(req, json, d);
        }

        return result;
    }

    private boolean configureDescriptor(StaplerRequest2 req, JSONObject json, Descriptor<?> d) throws FormException {
        // collapse the structure to remain backward compatible with the JSON structure before 1.
        String name = d.getJsonSafeClassName();
        JSONObject js = json.has(name) ? json.getJSONObject(name) : new JSONObject(); // if it doesn't have the property, the method returns invalid null object.
        json.putAll(js);
        return d.configure(req, js);
    }

    @Override
    public String getDisplayName() {
        return getDescriptor().getDisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.GlobalSecurityConfiguration_Description();
    }

    @Override
    public String getIconFileName() {
        return "symbol-lock-closed";
    }

    @Override
    public String getUrlName() {
        return "configureSecurity";
    }

    @Override
    public Permission getRequiredPermission() {
        return Jenkins.SYSTEM_READ;
    }

    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.222")
    public static final Predicate<Descriptor> FILTER = input -> input.getCategory() instanceof GlobalConfigurationCategory.Security;

    /**
     * @see Describable#getDescriptor()
     */
    @SuppressWarnings("unchecked")
    @Override
    public Descriptor<GlobalSecurityConfiguration> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    @Extension @Symbol("security")
    public static final class DescriptorImpl extends Descriptor<GlobalSecurityConfiguration> {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.GlobalSecurityConfiguration_DisplayName();
        }
    }
}
