/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

import hudson.search.Search;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Flavor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Data representation of the auto-completion candidates.
 * <p>
 * This object should be returned from your doAutoCompleteXYZ methods.
 *
 * @author Kohsuke Kawaguchi
 */
public class AutoCompletionCandidates implements HttpResponse {
    private final List<String> values = new ArrayList<String>();

    public AutoCompletionCandidates add(String v) {
        values.add(v);
        return this;
    }

    public AutoCompletionCandidates add(String... v) {
        values.addAll(Arrays.asList(v));
        return this;
    }

    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object o) throws IOException, ServletException {
        Search.Result r = new Search.Result();
        for (String value : values) {
            r.suggestions.add(new hudson.search.Search.Item(value));
        }
        rsp.serveExposedBean(req,r, Flavor.JSON);
    }
}
