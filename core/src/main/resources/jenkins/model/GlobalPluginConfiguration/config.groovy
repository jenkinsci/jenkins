package jenkins.model.GlobalPluginConfiguration

import hudson.Functions

def f=namespace(lib.FormTagLib)

// list config pages from plugins, if any
app.pluginManager.plugins.each { p ->
    if (Functions.hasView(p.plugin,"config.jelly")) {
        f.rowSet(name:"plugin") {
            f.invisibleEntry {
                input (type:"hidden", name:"name", value:p.shortName)
            }
            include(p.plugin, "config")
        }
    }
}
