package jenkins.security.DownloadSettings

def f = namespace(lib.FormTagLib)

f.section(title:_("Plugin Manager")) {
	f.entry(field: "useBrowser") {
		f.checkbox(title: _("Use browser for metadata download"))
	}
}
