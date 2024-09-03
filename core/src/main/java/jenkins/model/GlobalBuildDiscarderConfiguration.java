/*
 * The MIT License
 *
 * Copyright 2019 Daniel Beck
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

package jenkins.model;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.DescribableList;
import java.io.IOException;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Global configuration UI for background build discarders
 *
 * @see GlobalBuildDiscarderStrategy
 * @see BackgroundGlobalBuildDiscarder
 */
@Restricted(NoExternalUse.class)
@Extension @Symbol("buildDiscarders")
public class GlobalBuildDiscarderConfiguration extends GlobalConfiguration {
    public static GlobalBuildDiscarderConfiguration get() {
        return ExtensionList.lookupSingleton(GlobalBuildDiscarderConfiguration.class);
    }

    public GlobalBuildDiscarderConfiguration() {
        load();
    }

    private final DescribableList<GlobalBuildDiscarderStrategy, GlobalBuildDiscarderStrategyDescriptor> configuredBuildDiscarders =
            new DescribableList<>(this, List.of(new JobGlobalBuildDiscarderStrategy()));

    private Object readResolve() {
        configuredBuildDiscarders.setOwner(this);
        return this;
    }

    public DescribableList<GlobalBuildDiscarderStrategy, GlobalBuildDiscarderStrategyDescriptor> getConfiguredBuildDiscarders() {
        return configuredBuildDiscarders;
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        try {
            configuredBuildDiscarders.rebuildHetero(req, json, GlobalBuildDiscarderStrategyDescriptor.all(), "configuredBuildDiscarders");
            return true;
        } catch (IOException x) {
            throw new FormException(x, "configuredBuildDiscarders");
        }
    }
}
