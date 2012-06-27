package jenkins.plugins.ui_samples.FormFieldValidationWithContext;

import lib.JenkinsTagLib
import lib.FormTagLib

def f=namespace(FormTagLib.class)

t=namespace(JenkinsTagLib.class)

namespace("/lib/samples").sample(title:_("Context-sensitive form validation")) {
    p {
        raw(_("blurb.context"))
        raw(_("blurb.otheruse"))
        raw(_("blurb.contrived"))
    }

    f.form {
        f.entry(title:"States") {
            f.repeatableProperty(field:"states")
        }
    }
}
