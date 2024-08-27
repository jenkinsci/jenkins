/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

package jenkins.tasks.filters;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.util.DescribableList;
import java.io.IOException;
import java.util.List;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Configuration of the filter rules that are applied globally,
 * after filtering which rule applies on which builder
 *
 * @since 2.246
 */
@Extension
@Symbol("envVarsFilter")
@Restricted(Beta.class)
public class EnvVarsFilterGlobalConfiguration extends GlobalConfiguration {
    private DescribableList<EnvVarsFilterGlobalRule, Descriptor<EnvVarsFilterGlobalRule>> activatedGlobalRules =
            new DescribableList<>(this);

    public EnvVarsFilterGlobalConfiguration() {
        load();
    }

    public static EnvVarsFilterGlobalConfiguration get() {
        return GlobalConfiguration.all().get(EnvVarsFilterGlobalConfiguration.class);
    }

    // used by Jelly
    public static ExtensionList<Descriptor<EnvVarsFilterGlobalRule>> getAllGlobalRules() {
        return Jenkins.get().getDescriptorList(EnvVarsFilterGlobalRule.class);
    }

    public static List<EnvVarsFilterGlobalRule> getAllActivatedGlobalRules() {
        return get().activatedGlobalRules;
    }

    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Unclassified.class);
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        try {
            activatedGlobalRules.rebuildHetero(req, json, getAllGlobalRules(), "rules");
        } catch (IOException e) {
            throw new FormException(e, "rules");
        }

        this.save();

        return true;
    }
}
