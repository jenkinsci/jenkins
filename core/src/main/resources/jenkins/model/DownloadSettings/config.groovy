package jenkins.security.DownloadSettings

def f = namespace(lib.FormTagLib)

f.section(title: _("Download Preferences")) {
    f.entry(title: _("Use Browser"), field: "useBrowser") {
        f.checkbox()
    }
}
