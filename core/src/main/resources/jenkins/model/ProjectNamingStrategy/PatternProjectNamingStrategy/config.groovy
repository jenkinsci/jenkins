package jenkins.model.ProjectNamingStrategy.PatternProjectNamingStrategy


def f=namespace(lib.FormTagLib)

f.entry(title:_("namePattern"), field:"namePattern") {
    f.textbox(value:h.defaulted(instance?.namePattern, descriptor.DEFAULT_PATTERN),class:"fixed-width")
}

f.entry(title:_("description"), field:"description") {
    f.textbox()
}

f.entry() {
    f.checkbox(title:_("forceExistingJobs"), field:"forceExistingJobs")
}
