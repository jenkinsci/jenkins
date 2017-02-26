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
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.koshuke.stapler.simile.timeline.Event;
import org.koshuke.stapler.simile.timeline.TimelineEventList;

import java.io.IOException;
import java.util.Date;

/**
 * UI widget for showing the SIMILE timeline control.
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

    public TimelineEventList doData(StaplerRequest req, @QueryParameter long min, @QueryParameter long max) throws IOException {
        TimelineEventList result = new TimelineEventList();
        for (Run r : builds.byTimestamp(min,max)) {
            Event e = new Event();
            e.start = new Date(r.getStartTimeInMillis());
            e.end   = new Date(r.getStartTimeInMillis()+r.getDuration());
            e.title = r.getFullDisplayName();
            // what to put in the description?
            // e.description = "Longish description of event "+r.getFullDisplayName();
            // e.durationEvent = true;
            e.link = req.getContextPath()+'/'+r.getUrl();
            BallColor c = r.getIconColor();
            e.color = String.format("#%06X",c.getBaseColor().darker().getRGB()&0xFFFFFF);
            e.classname = "event-"+c.noAnime().toString()+" " + (c.isAnimated()?"animated":"");
            result.add(e);
        }
        return result;
    }

}
