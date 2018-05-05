/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.util.Collections;
import java.util.List;

/**
 * Obtains the structured form data from {@link StaplerRequest}.
 * See http://wiki.jenkins-ci.org/display/JENKINS/Structured+Form+Submission
 *
 * @author Kohsuke Kawaguchi
 */
public class StructuredForm {

    /**
     * @deprecated
     *      Use {@link StaplerRequest#getSubmittedForm()}. Since 1.238.
     */
    @Deprecated
    public static JSONObject get(StaplerRequest req) throws ServletException {
        return req.getSubmittedForm();
    }
    /**
     * Retrieves the property of the given object and returns it as a list of {@link JSONObject}.
     *
     * <p>
     * If the value doesn't exist, this method returns an empty list. If the value is
     * a {@link JSONObject}, this method will return a singleton list. If it's a {@link JSONArray},
     * the contents will be returned as a list.
     *
     * <p>
     * Because of the way structured form submission work, this is convenient way of
     * handling repeated multi-value entries.
     *
     * @since 1.233 
     */
    public static List<JSONObject> toList(JSONObject parent, String propertyName) {
        Object v = parent.get(propertyName);
        if(v==null)
            return Collections.emptyList();
        if(v instanceof JSONObject)
            return Collections.singletonList((JSONObject)v);
        if(v instanceof JSONArray)
            return (List)(JSONArray)v;

        throw new IllegalArgumentException();
    }
}
