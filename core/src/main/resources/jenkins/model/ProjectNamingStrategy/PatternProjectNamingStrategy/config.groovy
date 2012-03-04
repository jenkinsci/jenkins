package jenkins.model.GlobalProjectNamingStrategyConfiguration


def f=namespace(lib.FormTagLib)

f.entry(title:_("namePattern")) {
    f.textbox(name:"namePattern",value:h.defaulted(instance?.namePattern, descriptor.DEFAULT_PATTERN),class:"fixed-width")
}
f.entry(title:_("forceExistingJobs"), field:"forceExistingJobs") {
    f.checkbox(name:"forceExistingJobs")
}

