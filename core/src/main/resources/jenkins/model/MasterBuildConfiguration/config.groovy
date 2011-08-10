package jenkins.model.MasterBuildConfiguration

def f=namespace(lib.FormTagLib)

f.entry(title:_("# of executors"), field:"numExecutors") {
    f.textbox()
}
if (!app.slaves.isEmpty()) {
    f.entry(title:_("Labels"),field:"labelString") {
        f.textbox()
    }
    f.slave_mode(name:"master.mode",node:app)
}
