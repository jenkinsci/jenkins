/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.matrix;

import hudson.Extension;
import hudson.model.Hudson;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

/**
 * {@link Axis} that selects label expressions.
 *
 * @author Kohsuke Kawaguchi
 */
public class LabelAxis extends Axis {
    @DataBoundConstructor
    public LabelAxis(String name, List<String> values) {
        super(name, values);
    }

    @Override
    public boolean isSystem() {
        return true;
    }

    @Extension
    public static class DescriptorImpl extends AxisDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.LabelAxis_DisplayName();
        }

        /**
         * If there's no distributed build set up, it's pointless to provide this axis.
         */
        @Override
        public boolean isInstantiable() {
            Hudson h = Hudson.getInstance();
            return !h.getNodes().isEmpty() || !h.clouds.isEmpty();
        }
    }
}
