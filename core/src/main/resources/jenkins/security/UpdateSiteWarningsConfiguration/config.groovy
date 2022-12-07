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

package jenkins.security.UpdateSiteWarningsConfiguration

f = namespace(lib.FormTagLib)
st = namespace("jelly:stapler")

st.adjunct(includes: "jenkins.security.UpdateSiteWarningsConfiguration.style")

def printEntry(warning, title, checked) {
    f.block {
        f.checkbox(name: warning.id,
                title: title,
                checked: checked,
                class: 'hideWarnings')
        div(class: "setting-description") {
            a(warning.url, href: warning.url)
        }
    }
}

f.section(title:_("Hidden security warnings")) {

    f.advanced(title: _("Security warnings"), align:"left") {
        f.block {
            text(_("blurb"))
        }
        f.entry(title: _("Security warnings"),
                help: '/descriptorByName/UpdateSiteWarningsConfiguration/help') {
            table(width:"100%") {

                descriptor.applicableWarnings.each { warning ->
                    if (warning.type == hudson.model.UpdateSite.WarningType.CORE) {
                        printEntry(warning,
                                _("warning.core", warning.message),
                                !descriptor.isIgnored(warning))
                    }
                    else if (warning.type == hudson.model.UpdateSite.WarningType.PLUGIN) {
                        def plugin = descriptor.getPlugin(warning)
                        printEntry(warning,
                                _("warning.plugin", plugin.displayName, warning.message),
                                !descriptor.isIgnored(warning))
                    }
                }
            }
        }
    }
}
