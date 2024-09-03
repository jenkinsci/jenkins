/*
 * The MIT License
 *
 * Copyright (c) 2017 Oleg Nenashev.
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

package org.kohsuke.stapler;

import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

/**
 * Mocked version of {@link StaplerRequest2}.
 * @author Oleg Nenashev
 */
public class MockStaplerRequestBuilder {

    private final JenkinsRule r;

    private final List<AncestorImpl> ancestors = new ArrayList<>();
    private final TokenList tokens;
    final Map<String, Object> getters = new HashMap<>();
    private Stapler stapler;

    public MockStaplerRequestBuilder(@NonNull JenkinsRule r, String url) {
        this.r = r;
        this.tokens = new TokenList(url);
    }

    public MockStaplerRequestBuilder withStapler(Stapler stapler) {
        this.stapler = stapler;
        return this;
    }

    public MockStaplerRequestBuilder withGetter(String objectName, Object object) {
        this.getters.put(objectName, object);
        return this;
    }

    public MockStaplerRequestBuilder withAncestor(AncestorImpl ancestor) {
        this.ancestors.add(ancestor);
        return this;
    }

    public StaplerRequest2 build() {
        HttpServletRequest rawRequest = Mockito.mock(HttpServletRequest.class);
        return new RequestImpl(stapler != null ? stapler : new Stapler(), rawRequest, ancestors, tokens);
    }

}
