/*
 * The MIT License
 *
 * Copyright (c) 2026, Jan Faracik
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

package jenkins.model.menu.action;

import hudson.model.Action;
import java.util.List;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.model.menu.Group;
import jenkins.model.menu.Semantic;
import jenkins.model.menu.event.DropdownEvent;
import jenkins.model.menu.event.Event;
import jenkins.model.menu.event.LinkEvent;
import jenkins.model.menu.event.SplitButtonEvent;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Wraps an {@link Action} so that its URLs resolve against a given object rather than the page
 * the app bar is rendered on, e.g. a folder's actions shown on one of the folder's views.
 */
@Restricted(NoExternalUse.class)
public final class BaseUrlAction implements Action, IconSpec, ModelObjectWithContextMenu.ContextMenuVisibility {

    private final Action delegate;

    private final String url;

    private final String contextPath;

    private final String baseUrl;

    /**
     * @param delegate the action to wrap
     * @param url the URL of the object the action belongs to, relative to the Jenkins root, e.g. {@code job/folder/}
     */
    public BaseUrlAction(Action delegate, String url) {
        this.delegate = delegate;
        this.url = url;
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        this.contextPath = req != null ? req.getContextPath() : "";
        this.baseUrl = contextPath + "/" + url;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public String getIconFileName() {
        return delegate.getIconFileName();
    }

    @Override
    public String getIconClassName() {
        if (delegate instanceof IconSpec iconSpec) {
            return iconSpec.getIconClassName();
        }

        return null;
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public String getUrlName() {
        return delegate.getUrlName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public Group getGroup() {
        return delegate.getGroup();
    }

    @Override
    public Event getEvent() {
        return resolve(delegate.getEvent());
    }

    private Event resolve(Event event) {
        if (event instanceof SplitButtonEvent splitButtonEvent) {
            return SplitButtonEvent.of(resolve(splitButtonEvent.getEvent()), wrap(splitButtonEvent.getActions()));
        }

        if (event instanceof DropdownEvent dropdownEvent) {
            return DropdownEvent.of(wrap(dropdownEvent.getActions()));
        }

        if (!(event instanceof LinkEvent linkEvent) || linkEvent.getUrl() == null || !linkEvent.getUrl().startsWith("/")) {
            return event;
        }

        return LinkEvent.of(contextPath + linkEvent.getUrl(), linkEvent.getType());
    }

    private List<Action> wrap(List<Action> actions) {
        return actions.stream().map(action -> (Action) new BaseUrlAction(action, url)).toList();
    }

    @Override
    public Semantic getSemantic() {
        return delegate.getSemantic();
    }

    @Override
    public boolean isVisibleInContextMenu() {
        return delegate.isVisibleInContextMenu();
    }

    @Override
    public boolean isVisible() {
        if (delegate instanceof ModelObjectWithContextMenu.ContextMenuVisibility visibility) {
            return visibility.isVisible();
        }

        return true;
    }
}
