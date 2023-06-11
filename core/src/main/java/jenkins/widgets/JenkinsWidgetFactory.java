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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.View;
import hudson.widgets.Widget;
import java.util.Collection;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Add widgets annotated with @Extension, or added manually to Jenkins via <code>Jenkins.get().getWidgets().add(...)</code>
 *
 * @deprecated New widgets should provide a {@link WidgetFactory} instead of relying on this legacy lookup.
 */
@Extension
@Restricted(DoNotUse.class)
@Deprecated
@Symbol("jenkins")
public final class JenkinsWidgetFactory extends WidgetFactory<View, Widget> {
    @Override
    public Class<View> type() {
        return View.class;
    }

    @Override
    public Class<Widget> widgetType() {
        return Widget.class;
    }

    @NonNull
    @Override
    public Collection<Widget> createFor(@NonNull View target) {
        return Jenkins.get().getWidgets();
    }
}
