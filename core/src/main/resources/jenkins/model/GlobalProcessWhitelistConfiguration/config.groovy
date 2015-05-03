package jenkins.model.GlobalProcessWhitelistConfiguration


def f=namespace(lib.FormTagLib)

f.entry(title:_("Process cleanup whitelist"), field:"processCleanupWhitelist") {
    f.textbox()
}

