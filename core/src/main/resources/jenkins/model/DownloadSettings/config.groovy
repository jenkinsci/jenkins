package jenkins.security.DownloadSettings

def f = namespace(lib.FormTagLib)

f.section(title:_("Plugin Manager")) {
    f.entry() {
        f.checkbox(field: "useBrowser", title: _("Use browser for metadata download"))
    }
}
