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

import hudson.Functions;
import hudson.Util;
import hudson.util.RunList;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * UI widget for showing the timeline control.
 *
 * <p>
 * Return this from your "getTimeline" method.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.372
 */
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

    @Restricted(NoExternalUse.class)
    public JSONObject doData(StaplerRequest req, @QueryParameter long min, @QueryParameter long max, @QueryParameter long utcOffset) throws IOException {
        JSONObject data = new JSONObject();
        JSONArray events = new JSONArray();
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        int id = 1;
        for (Run<?, ?> r : builds.byTimestamp(min, max)) {
            JSONObject event = new JSONObject();
            event.accumulate("id", id++);
            event.accumulate("start", formatter.format(new Date(r.getStartTimeInMillis() + utcOffset*60*1000)));
            event.accumulate("end", formatter.format(new Date(r.getStartTimeInMillis() + utcOffset*60*1000 + r.getDuration())));
            event.accumulate("content", generateContent(r, req));

            BallColor c = r.getIconColor();
            String color = c.getIconClassName();
            String classes = "color-" + color + " event-" + c.noAnime().toString() + " " + (c.isAnimated() ? "animated" : "");
            event.accumulate("class", classes);
            events.add(event);
        }
        data.accumulate("events", events);
        return data;
    }

    private static String generateContent(Run run, StaplerRequest req) {
        return "<a href='" + req.getContextPath() + '/' + Functions.htmlAttributeEscape(run.getUrl()) + "'>" + Util.xmlEscape(run.getFullDisplayName()) + "</a>";
    }

}
