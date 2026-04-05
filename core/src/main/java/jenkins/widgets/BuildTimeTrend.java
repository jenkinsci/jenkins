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

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BallColor;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import jenkins.console.ConsoleUrlProvider;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

@Restricted(DoNotUse.class) // only for buildTimeTrend.jelly
public class BuildTimeTrend extends RunListProgressiveRendering {

    public boolean isAbstractProject(Job<?, ?> job) {
        return job instanceof AbstractProject;
    }

    @Override protected void calculate(Run<?, ?> build, JSONObject element) {
        BallColor iconColor = build.getIconColor();
        element.put("iconName", iconColor.getIconName());
        element.put("iconColorOrdinal", iconColor.ordinal());
        element.put("iconColorDescription", iconColor.getDescription());
        element.put("number", build.getNumber());
        element.put("displayName", build.getDisplayName());
        element.put("duration", build.getDuration());
        element.put("durationString", build.getDurationString());
        element.put("timestampString", build.getTimestampString());
        element.put("timestampString2", build.getTimestampString2());
        element.put("consoleUrl", ConsoleUrlProvider.getRedirectUrl(build));
        if (build instanceof AbstractBuild) {
            AbstractBuild<?, ?> b = (AbstractBuild) build;
            Node n = b.getBuiltOn();
            if (n == null) {
                String ns = b.getBuiltOnStr();
                if (ns != null && !ns.isEmpty()) {
                    element.put("builtOnStr", ns);
                }
            } else if (n != Jenkins.get()) {
                element.put("builtOn", n.getNodeName());
                element.put("builtOnStr", n.getDisplayName());
            } else {
                element.put("builtOnStr", hudson.model.Messages.Hudson_Computer_DisplayName());
            }
        }
    }

}
