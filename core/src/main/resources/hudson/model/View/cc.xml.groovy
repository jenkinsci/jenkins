/**
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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
package hudson.model.View

import hudson.Functions
import hudson.model.Items
import hudson.model.Job
import hudson.model.TopLevelItem
import org.kohsuke.stapler.Stapler

/**
 * Generate status XML compatible with CruiseControl.
 * See https://web.archive.org/web/20090830044629/http://confluence.public.thoughtworks.org/display/CI/Multiple+Project+Summary+Reporting+Standard
 */
def st = namespace("jelly:stapler")
st.contentType(value: "text/xml;charset=UTF-8")

def topLevelItems = Stapler.currentRequest.getParameter('recursive') != null
    ? Items.getAllItems(it.owner.itemGroup, TopLevelItem.class)
    : it.items

Projects {
    topLevelItems.each { item ->
        if (item instanceof Job) {
            def lb = item.lastCompletedBuild;
            if (lb != null) {
                Project(
                    name: item.fullDisplayName,
                    activity: item.building ? 'Building' : 'Sleeping',
                    lastBuildStatus: Functions.toCCStatus(item),
                    lastBuildLabel: lb.number,
                    lastBuildTime: lb.timestampString2,
                    webUrl: app.rootUrl+item.url
                )
            }
        }
    }
}
