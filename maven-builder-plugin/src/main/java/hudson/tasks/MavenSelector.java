/*
 * The MIT License
 * 
 * Copyright (c) 2016 CloudBess, Inc.
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
package hudson.tasks;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Item;
import hudson.model.Project;
import hudson.tasks.Maven.MavenInstallation;

/**
 * Extension point for selecting the {@link MavenInstallation} that should be used when running an {@link Item}
 *
 * @since 2.14
 */
public abstract class MavenSelector implements ExtensionPoint {

    /**
     * Gets the {@link MavenInstallation} associated with the project. Can be null.
     *
     * <p>
     * If the Maven installation can not be uniquely determined, it's often better to return just one of them, rather
     * than returning null, since this method is currently ultimately only used to decide the maven home folder and to
     * parse <tt>conf/settings.xml</tt> from.
     */
    @Nullable
    public abstract MavenInstallation selectMavenInstallation(@NonNull Item item);

    /**
     * Returns true if this {@link MavenSelector} is applicable to the given {@link Item}.
     *
     * @return true to use this {@link MavenSelector} to choose the maven installationfor that {@link Item}.
     */
    public abstract boolean isApplicable(@NonNull Class<? extends Item> jobType);

    /**
     * Returns all the registered {@link MavenSelector}s.
     */
    public static ExtensionList<MavenSelector> all() {
        return ExtensionList.lookup(MavenSelector.class);
    }

    /**
     * Returns a {@link MavenInstallation} for the provided {@link Item}. It does so by searching a
     * {@link MavenSelector} extension point that is applicable for that {@link Item} type.
     * 
     * If no {@link MavenSelector} matches the {@link Item} type and if the {@link Item} is a {@link Project}, a
     * {@link Maven} {@link Builder} will be searched and tried to get a {@link MavenInstallation} from it.
     * 
     * @param item to look a {@link MavenInstallation} for
     * @return the selected {@link MavenInstallation} for that item
     * @see {@link MavenSelector#isApplicable}
     * @see {@link Maven#getMaven}
     */
    @Nullable
    public static MavenInstallation obtainMavenInstallation(@NonNull Item item) {
        ExtensionList<MavenSelector> all = all();
        for (MavenSelector withMaven : all) {
            if (withMaven.isApplicable(item.getClass())) {
                return withMaven.selectMavenInstallation(item);
            }
        }

        // if there are no matches we go for the default behaviour of previous "ProjectWithMaven"
        if (item instanceof Project<?, ?>) {
            Maven m = ((Project<?, ?>) item).getBuildersList().get(Maven.class);
            if (m != null) {
                return m.getMaven();
            }
        }
        return null;
    }

}
