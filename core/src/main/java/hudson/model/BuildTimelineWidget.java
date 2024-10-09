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

import hudson.util.RunList;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * UI widget for showing the SIMILE timeline control.
 *
 * <p>
 * Return this from your "getTimeline" method.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.372
 * @deprecated since 2.431
 */
@Deprecated(since = "2.431")
public class BuildTimelineWidget {
    protected final RunList<?> builds;

    public BuildTimelineWidget(RunList<?> builds) {
        this.builds = builds.limit(20); // TODO instead render lazily
    }

    @Deprecated
    public Run<?, ?> getFirstBuild() {
        return builds.getFirstBuild();
    }

    @Deprecated
    public Run<?, ?> getLastBuild() {
        return builds.getLastBuild();
    }

    public HttpResponse doData(StaplerRequest2 req, @QueryParameter long min, @QueryParameter long max) {
        return new HttpResponse() {
            @Override
            public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
                JSONObject o = new JSONObject();
                o.put("events", JSONArray.fromObject(new ArrayList<>()));
                rsp.setContentType("text/javascript;charset=UTF-8");
                o.write(rsp.getWriter());
            }
        };
    }

}
