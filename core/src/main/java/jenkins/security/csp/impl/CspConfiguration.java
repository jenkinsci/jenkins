/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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

package jenkins.security.csp.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.model.PersistentDescriptor;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import jenkins.security.ResourceDomainConfiguration;
import jenkins.security.csp.AdvancedConfiguration;
import jenkins.security.csp.AdvancedConfigurationDescriptor;
import jenkins.security.csp.CspHeader;
import jenkins.security.csp.CspHeaderDecider;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

@Extension
@Symbol("contentSecurityPolicy")
@Restricted(NoExternalUse.class)
public class CspConfiguration extends GlobalConfiguration implements PersistentDescriptor {

    /**
     * Package-private to allow setting by {@link CspRecommendation} without saving.
     * Also exposes the "unconfigured" state as {@code null}, unlike the public getter used by Jelly.
     */
    protected Boolean enforce;

    private final DescribableList<AdvancedConfiguration, AdvancedConfigurationDescriptor> advanced = new DescribableList<>(this);

    @NonNull
    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    public boolean isEnforce() {
        return enforce != null && enforce;
    }

    @DataBoundSetter
    public void setEnforce(boolean enforce) {
        this.enforce = enforce;
        save();
    }

    // Make available to test in same package
    @Override
    protected XmlFile getConfigFile() {
        return super.getConfigFile();
    }

    @Restricted(DoNotUse.class) // Jelly
    public boolean isShowHeaderConfiguration() {
        final Optional<CspHeaderDecider> currentDecider = CspHeaderDecider.getCurrentDecider();
        return currentDecider.filter(cspHeaderDecider -> cspHeaderDecider instanceof ConfigurationHeaderDecider).isPresent();
    }

    @Restricted(DoNotUse.class) // Jelly
    public CspHeaderDecider getCurrentDecider() {
        return CspHeaderDecider.getCurrentDecider().orElse(null);
    }

    @POST
    public FormValidation doCheckEnforce(@QueryParameter boolean enforce) {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return FormValidation.ok();
        }
        if (!getConfigFile().exists()) {
            // The configuration has not been saved yet
            if (enforce) {
                if (ResourceDomainConfiguration.isResourceDomainConfigured()) {
                    return FormValidation.ok(Messages.CspConfiguration_UndefinedToTrueWithResourceDomain());
                }
                return FormValidation.okWithMarkup(Messages.CspConfiguration_UndefinedToTrueWithoutResourceDomain(Jenkins.get().getRootUrlFromRequest()));
            }
            // Warning because we're here just after the admin confirmed they wanted to set it up.
            return FormValidation.warning(Messages.CspConfiguration_UndefinedToFalse());
        }
        if (enforce && !ResourceDomainConfiguration.isResourceDomainConfigured()) {
            if (ExtensionList.lookupSingleton(CspConfiguration.class).isEnforce()) {
                return FormValidation.warningWithMarkup(Messages.CspConfiguration_TrueToTrueWithoutResourceDomain(Jenkins.get().getRootUrlFromRequest()));
            }
            return FormValidation.okWithMarkup(Messages.CspConfiguration_FalseToTrueWithoutResourceDomain(Jenkins.get().getRootUrlFromRequest()));
        }
        return FormValidation.ok();
    }

    public List<? extends AdvancedConfiguration> getAdvanced() {
        return advanced;
    }

    @DataBoundSetter
    public void setAdvanced(List<? extends AdvancedConfiguration> advanced) throws IOException {
        this.advanced.replaceBy(advanced);
        save();
    }

    /**
     * For Jelly
     * */
    public DescriptorExtensionList<AdvancedConfiguration, AdvancedConfigurationDescriptor> getAdvancedDescriptors() {
        return AdvancedConfiguration.all();
    }

    @Extension
    public static class ConfigurationHeaderDecider implements CspHeaderDecider {

        @Override
        public Optional<CspHeader> decide() {
            Boolean enforce = ExtensionList.lookupSingleton(CspConfiguration.class).enforce;

            if (enforce == null) {
                // If no configuration is present, use FallbackDecider to show initial UI
                return Optional.empty();
            }
            if (enforce) {
                return Optional.of(CspHeader.ContentSecurityPolicy);
            }
            return Optional.of(CspHeader.ContentSecurityPolicyReportOnly);
        }
    }
}
