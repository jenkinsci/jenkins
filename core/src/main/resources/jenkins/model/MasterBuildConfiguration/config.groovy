package jenkins.model.MasterBuildConfiguration

import jenkins.model.Jenkins

def f=namespace(lib.FormTagLib)

if (Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
    f.entry(title:_("# of executors"), field:"numExecutors") {
        f.number(clazz:"non-negative-number-required", min:0, step:1)
    }
}

f.entry(title:_("Labels"),field:"labelString") {
    f.textbox()
}
f.slave_mode(name:"master.mode",node:app)