package hudson.PluginWrapper

import jenkins.model.Jenkins

def l = namespace(lib.LayoutTagLib)
def f = namespace(lib.FormTagLib)

l.layout(permission: Jenkins.ADMINISTER) {
    def title = _("title", my.shortName)
    l.header(title:title)
    l.main_panel {
        h1 {
            l.icon(class: 'icon-error icon-xlg')
            text(" ")
            text(title)
        }
        p { raw _("msg",my.shortName) }
        f.form(method:"post",action:"doUninstall") {
            f.submit(value:_("Yes"))
        }
    }
}