/*
 * The MIT License
 *
 * Copyright (c) 2010, Winston.Prakash@Oracle.com
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

package hudson.views;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The Default MyViewsTabBar Extension for {@link MyViewsTabBar}.
 *
 * @author Winston Prakash
 * @since 1.378
 * @see MyViewsTabBar
 * @deprecated No longer used
 */
@Deprecated
public class DefaultMyViewsTabBar extends MyViewsTabBar {
    @DataBoundConstructor
    public DefaultMyViewsTabBar() {
    }

    @Extension @Symbol("standard")
    public static class DescriptorImpl extends MyViewsTabBarDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.DefaultMyViewsTabsBar_DisplayName();
            //return "Default My Views TabsBar";
        }
    }

}
