/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, id:cactusman
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

package hudson.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import jenkins.model.Jenkins;
import jenkins.model.item_category.StandaloneProjectsCategory;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Free-style software project.
 *
 * @author Kohsuke Kawaguchi
 */
public class FreeStyleProject extends Project<FreeStyleProject, FreeStyleBuild> implements TopLevelItem {

    /**
     * @deprecated as of 1.390
     */
    @Deprecated
    public FreeStyleProject(Jenkins parent, String name) {
        super(parent, name);
    }

    public FreeStyleProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    protected Class<FreeStyleBuild> getBuildClass() {
        return FreeStyleBuild.class;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Descriptor is instantiated as a field purely for backward compatibility.
     * Do not do this in your code. Put @Extension on your DescriptorImpl class instead.
     *
     * @deprecated as of 2.0
     *      Use injection
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_PKGPROTECT", justification = "for backward compatibility")
    public static /*almost final*/ DescriptorImpl DESCRIPTOR;

    @Extension(ordinal = 1000) @Symbol({"freeStyle", "freeStyleJob"})
    public static class DescriptorImpl extends AbstractProjectDescriptor {
        @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "for backward compatibility")
        public DescriptorImpl() {
            DESCRIPTOR = this;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.FreeStyleProject_DisplayName();
        }

        @Override
        public FreeStyleProject newInstance(ItemGroup parent, String name) {
            return new FreeStyleProject(parent, name);
        }

        @Override
        public String getDescription() {
            return Messages.FreeStyleProject_Description();
        }

        @Override
        public String getCategoryId() {
            return StandaloneProjectsCategory.ID;
        }

        @Override
        public String getIconFilePathPattern() {
            return (Jenkins.RESOURCE_PATH + "/images/:size/freestyleproject.png").replaceFirst("^/", "");
        }

        @Override
        public String getIconClassName() {
            return "symbol-freestyle-project";
        }

        static {
            IconSet.icons.addIcon(new Icon("icon-freestyle-project icon-sm", "16x16/freestyleproject.png", Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(new Icon("icon-freestyle-project icon-md", "24x24/freestyleproject.png", Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(new Icon("icon-freestyle-project icon-lg", "32x32/freestyleproject.png", Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(new Icon("icon-freestyle-project icon-xlg", "48x48/freestyleproject.png", Icon.ICON_XLARGE_STYLE));
        }
    }
}
