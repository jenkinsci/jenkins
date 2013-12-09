/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import hudson.Functions;
import hudson.model.BallColor;
import hudson.model.Run;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

@Restricted(DoNotUse.class) // only for buildListTable.jelly
public class BuildListTable extends RunListProgressiveRendering {

    @Override protected void calculate(Run<?,?> build, JSONObject element) {
        BallColor iconColor = build.getIconColor();
        element.put("iconColorOrdinal", iconColor.ordinal());
        element.put("iconColorDescription", iconColor.getDescription());
        element.put("url", build.getUrl());
        element.put("buildStatusUrl", build.getBuildStatusUrl());
        element.put("parentUrl", build.getParent().getUrl());
        element.put("parentFullDisplayName", Functions.breakableString(Functions.escape(build.getParent().getFullDisplayName())));
        element.put("displayName", build.getDisplayName());
        element.put("timestampString", build.getTimestampString());
        element.put("timestampString2", build.getTimestampString2());
        Run.Summary buildStatusSummary = build.getBuildStatusSummary();
        element.put("buildStatusSummaryWorse", buildStatusSummary.isWorse);
        element.put("buildStatusSummaryMessage", buildStatusSummary.message);
    }

}
