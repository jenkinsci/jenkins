package jenkins.security.DownloadSettings

def f = namespace(lib.FormTagLib)

f.section(title: _("Download Preferences")) {
    f.entry(title: _("Use Browser"), field: "useBrowser") {
        f.checkbox()
    }
    if (!instance.checkSignature || !hudson.model.DownloadService.signatureCheck) { // do not display this option by default
        f.entry(title: _("Check Signatures"), field: "checkSignature") {
            f.checkbox()
        }
    }
}
