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

def f = namespace(lib.FormTagLib)

div(class: "alert alert-warning", role: "alert") {

    form(method: "post", action: "${rootURL}/${my.url}/act") {
        f.submit(name: 'redirect', value: gettext("Learn more..."))
        f.submit(name: 'dismiss', value: gettext("Dismiss"))
    }

    text(gettext("blurb"))

    ul(style: "list-style-type: none;") {
        if (my.queueItemAuthenticatorPresent) {
            li(raw(gettext("queueItemAuthenticatorPresent")))


            if (my.queueItemAuthenticatorConfigured) {
                li(raw(gettext("queueItemAuthenticatorConfigured")))


                if (my.anyBuildLaunchedAsSystemWithAuthenticatorPresent) {
                    li(raw(gettext("anyBuildLaunchedAsSystem", rootURL)))
                    form(method: "post", action: "${rootURL}/${my.url}/act") {
                        f.submit(name: 'reset', value: gettext("Reset"))
                    }
                }
                // else: This monitor will not be displayed


            } else {
                li(raw(gettext("noQueueItemAuthenticatorConfigured", rootURL)))
            }

        } else {
            li(raw(gettext("noQueueItemAuthenticatorPresent")))
        }

    }
}
