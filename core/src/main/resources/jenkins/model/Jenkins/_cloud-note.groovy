package jenkins.model.Jenkins

import hudson.slaves.Cloud

// TODO remove this once it's been long enough that users got used to this.

def f = namespace(lib.FormTagLib)

def clouds = Cloud.all()

if (!clouds.isEmpty()) {
    f.section(title: _("Cloud")) {
        f.block {
            div(class: 'alert alert-info') {
                raw(_("note", rootURL))
            }
        }
    }
}
