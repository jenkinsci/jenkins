package jenkins.model.JenkinsLocationConfiguration

import hudson.Functions

def f=namespace(lib.FormTagLib)

f.section(title:gettext("Jenkins Location")) {
    f.entry(title:gettext("Jenkins URL"), field:"url") {
        f.textbox(default: Functions.inferHudsonURL(request))
    }
    f.entry(title:gettext("System Admin e-mail address"), field:"adminAddress") {
        f.textbox()
    }
}
