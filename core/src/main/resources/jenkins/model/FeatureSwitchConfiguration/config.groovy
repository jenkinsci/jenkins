package jenkins.model.FeatureSwitchConfiguration

def f = namespace(lib.FormTagLib)

f.section(title: _("Disable experimental feature(s)")) {
    f.entry(field: "disabledSet") {
        f.enumSet()
    }

}
