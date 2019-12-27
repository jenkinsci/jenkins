package jenkins.model.JenkinsLocationConfiguration

import hudson.Functions
import jenkins.model.Jenkins

def f=namespace(lib.FormTagLib)

f.section(title:_("Jenkins Location")) {
    if (Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
        f.entry(title: _("Jenkins URL"), field: "url") {
            f.textbox(default: Functions.inferHudsonURL(request))
        }
    }
    f.entry(title:_("System Admin e-mail address"), field:"adminAddress") {
        f.textbox()
    }
}
