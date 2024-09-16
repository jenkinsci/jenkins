/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

package jenkins.management.AdministrativeMonitorsConfiguration

import hudson.model.AdministrativeMonitor

f = namespace(lib.FormTagLib)
st = namespace("jelly:stapler")

f.section(title: _("Administrative monitors configuration")) {
    f.advanced(title: _("Administrative monitors")) {
        f.entry(title: _("Enabled administrative monitors"), description: _("blurb")) {
            for (AdministrativeMonitor am : new ArrayList<>(AdministrativeMonitor.all())
                    .sort({ o1, o2 -> o1.getDisplayName() <=> o2.getDisplayName() })) {
                div(style: "margin-bottom: 5px") {
                    div(class: "jenkins-checkbox-help-wrapper") {
                        f.checkbox(name: "administrativeMonitor",
                                title: am.displayName,
                                checked: am.enabled,
                                json: am.id)
                        if (am.isSecurity()) {
                            span(style: 'margin-left: 0.5rem', class: 'am-badge', _("Security"))
                        }
                    }
                    div(class: "jenkins-checkbox__description") {
                        st.include(it: am, page: "description", optional: true)
                    }
                }
            }
        }
    }
}
