/*
 * The MIT License
 *
 * Copyright (c) 2012, Dominik Bartholdi
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Configures the project naming strategy.
 *
 * @author Dominik Bartholdi (imod)
 */
@Extension(ordinal = 250) @Symbol("projectNamingStrategy")
public class GlobalProjectNamingStrategyConfiguration extends GlobalConfiguration {

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws hudson.model.Descriptor.FormException {
        // for compatibility reasons, the actual value is stored in Jenkins
        Jenkins j = Jenkins.get();
        final JSONObject optJSONObject = json.optJSONObject("useProjectNamingStrategy");
        if (optJSONObject != null) {
            final JSONObject strategyObject = optJSONObject.getJSONObject("namingStrategy");
            final String className = strategyObject.getString("$class");
            try {
                Class clazz = Class.forName(className, true, j.getPluginManager().uberClassLoader);
                final ProjectNamingStrategy strategy = (ProjectNamingStrategy) req.bindJSON(clazz, strategyObject);
                j.setProjectNamingStrategy(strategy);
            } catch (ClassNotFoundException e) {
                throw new FormException(e, "namingStrategy");
            }
        }
        if (j.getProjectNamingStrategy() == null) {
            j.setProjectNamingStrategy(ProjectNamingStrategy.DEFAULT_NAMING_STRATEGY);
        }
        return true;
    }

    @NonNull
    @Override
    public Permission getRequiredGlobalConfigPagePermission() {
        return Jenkins.MANAGE;
    }
}
