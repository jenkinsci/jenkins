/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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

package jenkins.security.QueueItemAuthenticatorMonitor

import jenkins.model.Jenkins
import jenkins.security.QueueItemAuthenticatorMonitor

def f=namespace(lib.FormTagLib)
def l=namespace(lib.LayoutTagLib)
def st=namespace("jelly:stapler")

l.layout(norefresh:true, permission:app.ADMINISTER, title:my.displayName) {
    l.main_panel {
        h1 {
            l.icon(class: 'icon-secure icon-xlg')
            text(my.displayName)
        }

        p(raw(_('blurb')))

        table(class: 'pane bigtable') {
            tr {
                th(_('Project'))
                th(_('Most Recent Builds Run as SYSTEM'))
            }
            QueueItemAuthenticatorMonitor.buildsLaunchedAsSystemWithAuthenticatorPresentByJob.toSorted { Jenkins.get().getItemByFullName(it.key)?.fullDisplayName?:it.key }.each { jobName, buildReferences ->
                def item = Jenkins.get().getItemByFullName(jobName)


                tr {
                    td {
                        if (item == null) {
                            // deleted?
                            text(jobName)
                        } else {
                            a(class: 'model-link inside', href: "${rootURL}/${item.url}", item?.fullDisplayName)
                        }
                    }
                    td {
                        buildReferences.descendingSet().each { reference ->
                            if (reference.buildNumber != null) {
                                def build = item?.getBuildByNumber((int) reference.buildNumber)
                                if (build == null) {
                                    text("#${reference.buildNumber}")
                                } else {
                                    a(class: 'model-link inside', href: "${rootURL}/${build.url}", build.displayName)
                                }
                            }
                        }
                    }
                }
            }
        }

        f.form(method:'post', name:'config', action:'act') {
            f.bottomButtonBar {
                f.submit(name:'reset', value:_("Clear"))
            }
        }
    }
}

