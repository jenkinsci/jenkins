package jenkins.model.MasterBuildConfiguration

def f=namespace(lib.FormTagLib)

f.entry(title:_("# of executors"), field:"numExecutors") {
    f.number(clazz:"non-negative-number-required", min:0, step:1)
}
f.entry(title:_("Labels"),field:"labelString") {
    f.textbox()
}
f.agent_mode(name:"builtin.mode",node:app)
