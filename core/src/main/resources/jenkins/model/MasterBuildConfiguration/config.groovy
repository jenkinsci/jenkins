package jenkins.model.MasterBuildConfiguration

def f=namespace(lib.FormTagLib)

f.entry(title:_("# of executors"), field:"numExecutors") {
    f.number(clazz:"number", min:0, step:1)
}
f.entry(title:_("Labels"),field:"labelString") {
    f.textbox()
}
f.slave_mode(name:"master.mode",node:app)
