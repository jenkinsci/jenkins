/*
 * The MIT License
 *
 * Copyright (c) 2012, CloudBees, Inc.
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
package jenkins;

import hudson.ExtensionComponent;
import hudson.ExtensionFinder;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AdministrativeMonitor;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import jenkins.model.Jenkins;

/**
 * Filters out {@link ExtensionComponent}s discovered by {@link ExtensionFinder}s,
 * as if they were never discovered.
 *
 * <p>
 * This is useful for those who are deploying restricted/simplified version of Jenkins
 * by reducing the functionality.
 *
 * <p>
 * Because of the way {@link ExtensionFinder} works, even when an extension component
 * is rejected by a filter, its instance still gets created first.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.472
 * @see DescriptorVisibilityFilter
 * @see ExtensionComponentSet#filtered()
 */
public abstract class ExtensionFilter implements ExtensionPoint {
    /**
     * Checks if a newly discovered extension is allowed to participate into Jenkins.
     *
     * <p>
     * To filter {@link Descriptor}s based on the {@link Describable} subtypes, do as follows:
     *
     * <pre>
     * return !component.isDescriptorOf(Builder.class);
     * </pre>
     *
     * @param type
     *      The type of the extension that we are discovering. This is not the actual instance
     *      type, but the contract type, such as {@link Descriptor}, {@link AdministrativeMonitor}, etc.
     * @return
     *      true to let the component into Jenkins. false to drop it and pretend
     *      as if it didn't exist. When any one of {@link ExtensionFilter}s veto
     *      a component, it gets dropped.
     */
    public abstract <T> boolean allows(Class<T> type, ExtensionComponent<T> component);

    public static <T> boolean isAllowed(Class<T> type, ExtensionComponent<T> component) {
        // to avoid infinite recursion, those extension points are handled differently.
        if (type==ExtensionFilter.class || type==ExtensionFinder.class)
            return true;

        for (ExtensionFilter f : all())
            if (!f.allows(type, component))
                return false;
        return true;
    }

    /**
     * All registered {@link ExtensionFilter} instances.
     */
    public static ExtensionList<ExtensionFilter> all() {
        return ExtensionList.lookup(ExtensionFilter.class);
    }
}
