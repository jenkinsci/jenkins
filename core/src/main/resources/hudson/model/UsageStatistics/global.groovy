package hudson.model.UsageStatistics

def f = namespace(lib.FormTagLib)

f.section(title: _("Usage Statistics")) {
    f.block() {
        f.checkbox(field: "shouldCollect", checked: instance.shouldCollect, title: _("statsBlurb"))
    }
}
