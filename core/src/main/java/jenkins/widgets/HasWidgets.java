/*
 * The MIT License
 *
 * Copyright 2023, CloudBees Inc.
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

package jenkins.widgets;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.widgets.Widget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.stapler.StaplerAccessibleType;
import jenkins.security.stapler.StaplerDispatchable;

/**
 * Classes implementing this interface can have widgets, and they can be accessed using relative urls "widget/widgetName"
 * @since 2.410
 */
@StaplerAccessibleType
public interface HasWidgets {
    Logger LOGGER = Logger.getLogger(HasWidgets.class.getName());

    /**
     * @return the list of widgets attached to the object.
     */
    default List<Widget> getWidgets() {
        List<Widget> result = new ArrayList<>();
        WidgetFactory
                .factoriesFor(getClass(), Widget.class)
                .forEach(wf -> {
                    try {
                        Collection<Widget> wfResult = wf.createFor(wf.type().cast(this));
                        for (Widget w : wfResult) {
                            if (wf.widgetType().isInstance(w)) {
                                result.add(w);
                            } else {
                                LOGGER.log(Level.WARNING, "Widget from {0} for {1} included {2} not assignable to {3}", new Object[] {wf, this, w, wf.widgetType()});
                            }
                        }
                    } catch (RuntimeException e) {
                        LOGGER.log(Level.WARNING, "Could not load all widgets from " + wf + " for " + this, e);
                    }
                });
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the named widget, or <code>null</code> if it does not exist.
     * Defaults to iterating on widgets and filtering based on the defined <code>urlName</code>.
     *
     * @param name the name of the widget within the current context.
     * @return the named widget, or <code>null</code> if it does not exist.
     */
    @CheckForNull
    @StaplerDispatchable
    default Widget getWidget(String name) {
        if (name == null) {
            return null;
        }
        return getWidgets().stream().filter(w -> name.equals(w.getUrlName())).findFirst().orElse(null);
    }
}
