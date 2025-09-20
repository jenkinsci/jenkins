/*
 * The MIT License
 *
 * Copyright (c) 2013, Red Hat, Inc.
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

package hudson.cli.handlers;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.cli.declarative.OptionHandlerExtension;
import hudson.model.View;
import hudson.model.ViewGroup;
import java.util.StringTokenizer;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;
import org.springframework.security.access.AccessDeniedException;

/**
 * Refers to {@link View} by its name.
 *
 * <p>
 * For example:
 * <dl>
 *   <dt>my_view_name</dt><dd>refers to a top level view with given name.</dd>
 *   <dt>nested/inner</dt><dd>refers to a view named {@code inner} inside of a top level view group named {@code nested}.</dd>
 * </dl>
 *
 * <p>
 * View name is a non-empty sequence of {@link View} names delimited by '/'.
 * Handler traverse the view names from left to right. First one is expected to
 * be a top level view and all but the last one are expected to be instances of
 * {@link ViewGroup}. Handler fails to resolve view provided a view with given
 * name does not exist or a user was not granted {@link View#READ} permission.
 *
 * @author ogondza
 * @since 1.538
 */
@OptionHandlerExtension
public class ViewOptionHandler extends OptionHandler<View> {

    public ViewOptionHandler(CmdLineParser parser, OptionDef option, Setter<View> setter) {

        super(parser, option, setter);
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {

        setter.addValue(getView(params.getParameter(0)));
        return 1;
    }

    /**
     *
     * Gets a view by its name
     * Note: Personal user's views aren't supported now.
     *
     * @param name A view name
     * @return The {@link View} instance. Null if name is empty string
     * @throws IllegalArgumentException
     *      If the view isn't found
     * @throws IllegalStateException
     *      If cannot get active Jenkins instance or view can't contain a views
     * @throws AccessDeniedException
     *      If user doesn't have a READ permission for the view
     * @since 1.618
     */
    @CheckForNull
    public View getView(final String name) {

        ViewGroup group = Jenkins.get();
        View view = null;

        final StringTokenizer tok = new StringTokenizer(name, "/");
        while (tok.hasMoreTokens()) {

            String viewName = tok.nextToken();

            view = group.getView(viewName);
            if (view == null) {
                group.checkPermission(View.READ);
                throw new IllegalArgumentException(String.format(
                        "No view named %s inside view %s",
                        viewName, group.getDisplayName()
                ));
            }
            view.checkPermission(View.READ);
            if (view instanceof ViewGroup) {
                group = (ViewGroup) view;
            } else if (tok.hasMoreTokens()) {
                throw new IllegalStateException(view.getViewName() + " view can not contain views");
            }
        }

        return view;
    }

    @Override
    public String getDefaultMetaVariable() {

        return "VIEW";
    }
}
