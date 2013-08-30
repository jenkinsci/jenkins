/*
The MIT License

Copyright (c) 2013, CloudBees, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package jenkins.diagnostics.ooom.OutOfOrderBuildMonitor

import jenkins.diagnostics.ooom.BuildPtr
import jenkins.diagnostics.ooom.Problem;

def f = namespace(lib.FormTagLib)

if (my.isFixingActive()) {
    div(class:"info") {
        raw _("inProgress",my.url)
    }
} else if (my.logFile.exists()) {
    form(method:"POST",action:"${my.url}/dismiss",name:"dismissOutOfOrderBuilds") {
        raw _("completed",my.url)
        f.submit(name:"dismiss",value:_("Dismiss this message"))
    }
}

if (!my.problems.isEmpty()) {
    form(method:"POST",action:"${my.url}/fix",name:"fixOutOfOrderBuilds") {
        div(class:"warning") {
            raw _("buildsAreOutOfOrder")
        }
        ul {
            my.problems.each { Problem p ->
                li {

                    raw(_("problem",
                            p.countInconsistencies(),
                            p.job.fullDisplayName,
                            rootURL+'/'+p.job.url))

                    text(" : ")
                    p.offenders.each { BuildPtr o ->
                        a(href:rootURL+'/'+p.job.url+'/'+o.n, "#${o.n}")

                        raw(" ")
                    }
                }
            }
        }

        div(align:"right") {
            f.submit(name:"fix",value:_("Correct those problems by moving offending records to a backup folder"))
        }
    }
}
