/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

package jenkins.console;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.appearance.AppearanceCategory;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Allows administrators to activate and sort {@link ConsoleUrlProvider} extensions to set defaults for all users.
 * @see ConsoleUrlProviderUserProperty
 * @since 2.433
 */
@Extension
@Symbol("consoleUrlProvider")
@Restricted(NoExternalUse.class)
public class ConsoleUrlProviderGlobalConfiguration extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(ConsoleUrlProviderGlobalConfiguration.class.getName());

    private List<ConsoleUrlProvider> providers;

    public ConsoleUrlProviderGlobalConfiguration() {
        load();
    }

    @NonNull
    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(AppearanceCategory.class);
    }

    public List<ConsoleUrlProvider> getProviders() {
        return providers;
    }

    @DataBoundSetter
    public void setProviders(List<ConsoleUrlProvider> providers) {
        this.providers = providers;
        save();
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        // We have to null out providers before data binding to allow all providers to be deleted in the config UI.
        // We use a BulkChange to avoid double saves in other cases.
        try (BulkChange bc = new BulkChange(this)) {
            providers = null;
            req.bindJSON(this, json);
            bc.commit();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save " + getConfigFile(), e);
        }
        return true;
    }

    public boolean isEnabled() {
        return ConsoleUrlProvider.isEnabled();
    }

    public static ConsoleUrlProviderGlobalConfiguration get() {
        return ExtensionList.lookupSingleton(ConsoleUrlProviderGlobalConfiguration.class);
    }

    public List<? extends Descriptor<ConsoleUrlProvider>> getProvidersDescriptors() {
        // For the global configuration, the default provider will always be consulted as a last resort, and since it
        // handles all builds, there is no reason to ever select it explicitly.
        return Jenkins.get().getDescriptorList(ConsoleUrlProvider.class).stream()
                .filter(d -> !(d instanceof DefaultConsoleUrlProvider.DescriptorImpl))
                .collect(Collectors.toList());
    }
}
