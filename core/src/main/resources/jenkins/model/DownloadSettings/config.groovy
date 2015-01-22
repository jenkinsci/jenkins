package jenkins.security.DownloadSettings

def f = namespace(lib.FormTagLib)

// TODO avoid indentation somehow
f.entry(field: "useBrowser") {
    f.checkbox(title: _("Use browser for metadata download"))
}
