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

package jenkins.security.csp;

import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import java.util.Optional;
import jenkins.model.Jenkins;
import jenkins.security.csp.impl.CspConfiguration;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Add more advanced options to the {@link jenkins.security.csp.impl.CspConfiguration} UI.
 *
 * @since 2.539
 */
@Restricted(Beta.class)
public abstract class AdvancedConfiguration implements Describable<AdvancedConfiguration>, ExtensionPoint {
    public static DescriptorExtensionList<AdvancedConfiguration, AdvancedConfigurationDescriptor> all() {
        return Jenkins.get().getDescriptorList(AdvancedConfiguration.class);
    }

    /**
     * Return the currently configured {@link jenkins.security.csp.AdvancedConfiguration}, if any.
     *
     * @param clazz the {@link jenkins.security.csp.AdvancedConfiguration} type to look up
     * @param <T> the {@link jenkins.security.csp.AdvancedConfiguration} type to look up
     * @return the configured instance, if any
     */
    public static <T extends AdvancedConfiguration> Optional<T> getCurrent(Class<T> clazz) {
        return ExtensionList.lookupSingleton(CspConfiguration.class).getAdvanced().stream()
                        .filter(a -> a.getClass() == clazz)
                        .map(clazz::cast)
                        .findFirst();
    }
}
