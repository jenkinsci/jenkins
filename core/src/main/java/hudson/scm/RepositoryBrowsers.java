/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Stephen Connolly
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

package hudson.scm;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.util.DescriptorList;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * List of all installed {@link RepositoryBrowsers}.
 *
 * @author Kohsuke Kawaguchi
 */
public class RepositoryBrowsers {
    /**
     * List of all installed {@link RepositoryBrowsers}.
     *
     * @deprecated as of 1.286.
     *      Use {@link RepositoryBrowser#all()} for read access and {@link Extension} for registration.
     */
    @Deprecated
    public static final List<Descriptor<RepositoryBrowser<?>>> LIST = new DescriptorList<RepositoryBrowser<?>>((Class) RepositoryBrowser.class);

    /**
     * Only returns those {@link RepositoryBrowser} descriptors that extend from the given type.
     */
    public static List<Descriptor<RepositoryBrowser<?>>> filter(Class<? extends RepositoryBrowser> t) {
        List<Descriptor<RepositoryBrowser<?>>> r = new ArrayList<>();
        for (Descriptor<RepositoryBrowser<?>> d : RepositoryBrowser.all())
            if (d.isSubTypeOf(t))
                r.add(d);
        return r;
    }

    /**
     * Creates an instance of {@link RepositoryBrowser} from a form submission.
     *
     * @deprecated since 2008-06-19.
     *      Use {@link #createInstance(Class, StaplerRequest2, JSONObject, String)}.
     */
    @Deprecated
    public static <T extends RepositoryBrowser>
    T createInstance(Class<T> type, StaplerRequest req, String fieldName) throws FormException {
        List<Descriptor<RepositoryBrowser<?>>> list = filter(type);
        String value = req.getParameter(fieldName);
        if (value == null || value.equals("auto"))
            return null;

        // TODO: There was a TODO in the original code, which presumes passing something meaningful to the newInstance() JSON param
        // Now we just pass empty JSON in order to make the code compliant with the defined interface
        final JSONObject emptyJSON = new JSONObject();
        return type.cast(list.get(Integer.parseInt(value)).newInstance(req, emptyJSON));
    }

    /**
     * Creates an instance of {@link RepositoryBrowser} from a form submission.
     *
     * @since 2.475
     */
    public static <T extends RepositoryBrowser>
    T createInstance(Class<T> type, StaplerRequest2 req, JSONObject parent, String fieldName) throws FormException {
        JSONObject o = (JSONObject) parent.get(fieldName);
        if (o == null) return null;

        return req.bindJSON(type, o);
    }

    /**
     * @deprecated use {@link #createInstance(Class, StaplerRequest2, JSONObject, String)}
     * @since 1.227
     */
    @Deprecated
    public static <T extends RepositoryBrowser>
    T createInstance(Class<T> type, StaplerRequest req, JSONObject parent, String fieldName) throws FormException {
        return createInstance(type, StaplerRequest.toStaplerRequest2(req), parent, fieldName);
    }
}
