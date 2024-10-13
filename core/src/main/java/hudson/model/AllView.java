/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor.FormException;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * {@link View} that contains everything.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.269
 */
public class AllView extends View {

    /**
     * The name of the default {@link AllView}. An {@link AllView} with this name will get a localized display name.
     * Other {@link AllView} instances will be assumed to have been created by the user and thus will use the
     * name the user created them with.
     *
     * @since 2.37
     */
    public static final String DEFAULT_VIEW_NAME = "all";

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(AllView.class.getName());

    @DataBoundConstructor
    public AllView(String name) {
        super(name);
    }

    public AllView(String name, ViewGroup owner) {
        this(name);
        this.owner = owner;
    }

    @Override
    public boolean isEditable() {
        return false;
    }

    @Override
    public boolean contains(TopLevelItem item) {
        return true;
    }

    @Override
    public String getDisplayName() {
        return DEFAULT_VIEW_NAME.equals(name) ? Messages.Hudson_ViewName() : name;
    }

    @RequirePOST
    @Override
    public Item doCreateItem(StaplerRequest2 req, StaplerResponse2 rsp)
            throws IOException, ServletException {
        ItemGroup<? extends TopLevelItem> ig = getOwner().getItemGroup();
        if (ig instanceof ModifiableItemGroup)
            return ((ModifiableItemGroup<? extends TopLevelItem>) ig).doCreateItem(req, rsp);
        return null;
    }

    @Override
    public Collection<TopLevelItem> getItems() {
        return (Collection) getOwner().getItemGroup().getItems();
    }

    @Override
    public String getPostConstructLandingPage() {
        return ""; // there's no configuration page
    }

    @Override
    protected void submit(StaplerRequest2 req) throws IOException, ServletException, FormException {
        if (Util.isOverridden(AllView.class, getClass(), "submit", StaplerRequest.class)) {
            try {
                submit(StaplerRequest.fromStaplerRequest2(req));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            // noop
        }
    }

    /**
     * @deprecated use {@link #submit(StaplerRequest2)}
     */
    @Deprecated
    @Override
    protected void submit(StaplerRequest req) throws IOException, javax.servlet.ServletException, FormException {
        // noop
    }

    /**
     * Corrects the name of the {@link AllView} if and only if the {@link AllView} is the primary view and
     * its name is one of the localized forms of {@link Messages#_Hudson_ViewName()} and the user has not opted out of
     * fixing the view name by setting the system property {@code hudson.mode.AllView.JENKINS-38606} to {@code false}.
     * Use this method to round-trip the primary view name, e.g.
     * {@code primaryView = migrateLegacyPrimaryAllViewLocalizedName(views, primaryView)}
     * <p>
     * NOTE: we can only fix the localized name of an {@link AllView} if it is the primary view as otherwise urls
     * would change, whereas the primary view is special and does not normally get accessed by the
     * {@code /view/_name_} url. (Also note that there are some cases where the primary view will get accessed by
     * its {@code /view/_name_} url which will then fall back to the primary view)
     *
     * @param views the list of views.
     * @param primaryView the current primary view name.
     * @return the primary view name - this will be the same as the provided primary view name unless a JENKINS-38606
     * matching name is detected, in which case this will be the new name of the primary view.
     * @since 2.37
     */
    @NonNull
    public static String migrateLegacyPrimaryAllViewLocalizedName(@NonNull List<View> views,
                                                                  @NonNull String primaryView) {
        if (DEFAULT_VIEW_NAME.equals(primaryView)) {
            // modern name, we are safe
            return primaryView;
        }
        if (SystemProperties.getBoolean(AllView.class.getName() + ".JENKINS-38606", true)) {
            AllView allView = null;
            for (View v : views) {
                if (DEFAULT_VIEW_NAME.equals(v.getViewName())) {
                    // name conflict, we cannot rename the all view anyway
                    return primaryView;
                }
                if (Objects.equals(v.getViewName(), primaryView)) {
                    if (v instanceof AllView) {
                        allView = (AllView) v;
                    } else {
                        // none of our business fixing as we can only safely fix the primary view
                        return primaryView;
                    }
                }
            }
            if (allView != null) {
                // the primary view is an AllView but using a non-default name
                for (Locale l : Locale.getAvailableLocales()) {
                    if (primaryView.equals(Messages._Hudson_ViewName().toString(l))) {
                        // bingo JENKINS-38606 detected
                        LOGGER.log(Level.INFO,
                                "JENKINS-38606 detected for AllView in {0}; renaming view from {1} to {2}",
                                new Object[] {allView.owner, primaryView, DEFAULT_VIEW_NAME});
                        allView.name = DEFAULT_VIEW_NAME;
                        return DEFAULT_VIEW_NAME;
                    }
                }
            }
        }
        return primaryView;
    }

    @Extension @Symbol("all")
    public static final class DescriptorImpl extends ViewDescriptor {
        @Override
        public boolean isApplicableIn(ViewGroup owner) {
            for (View v : owner.getViews()) {
                if (v instanceof AllView) {
                    return false;
                }
            }
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.Hudson_ViewName();
        }
    }
}
