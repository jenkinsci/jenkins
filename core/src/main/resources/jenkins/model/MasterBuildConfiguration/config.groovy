package jenkins.model.MasterBuildConfiguration

def f=namespace(lib.FormTagLib)

f.entry(title:gettext("# of executors"), field:"numExecutors") {
    f.number(clazz:"non-negative-number-required", min:0, step:1)
}
f.entry(title:gettext("Labels"),field:"labelString") {
    f.textbox()
}
f.slave_mode(name:"builtin.mode",node:app)
