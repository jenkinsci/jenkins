package jenkins.model.ProjectNamingStrategy.PatternProjectNamingStrategy


def f=namespace(lib.FormTagLib)

f.entry(title:gettext("namePattern"), field:"namePattern") {
    f.textbox(value:h.defaulted(instance?.namePattern, descriptor.DEFAULT_PATTERN),class:"fixed-width")
}

f.entry(title:gettext("description"), field:"description") {
    f.textbox()
}

f.entry() {
    f.checkbox(title:gettext("forceExistingJobs"), field:"forceExistingJobs")
}
