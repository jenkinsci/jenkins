/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Items;
import hudson.model.Job;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Defines a {@link BuildDiscarder}.
 * @since TODO
 */
public class BuildDiscarderProperty extends OptionalJobProperty<Job<?,?>> {

    private final BuildDiscarder strategy;

    @DataBoundConstructor
    public BuildDiscarderProperty(BuildDiscarder strategy) {
        this.strategy = strategy;
    }

    public BuildDiscarder getStrategy() {
        return strategy;
    }

    @Extension
    public static class DescriptorImpl extends OptionalJobPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.BuildDiscarderProperty_displayName();
        }

        static {
            Items.XSTREAM2.addCompatibilityAlias("org.jenkinsci.plugins.workflow.job.properties.BuildDiscarderProperty", BuildDiscarderProperty.class);
        }

    }

    @Extension
    public static class ConditionallyHidden extends DescriptorVisibilityFilter {

        @SuppressWarnings("rawtypes")
        @Override
        public boolean filter(Object context, Descriptor descriptor) {
            if (descriptor instanceof DescriptorImpl && context instanceof Job) {
                return ((Job) context).supportsLogRotator();
            }
            return true;
        }

    }

}
