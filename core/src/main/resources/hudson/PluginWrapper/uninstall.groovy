package hudson.PluginWrapper

import jenkins.model.Jenkins

def l = namespace(lib.LayoutTagLib)
def f = namespace(lib.FormTagLib)

l.layout(permission: Jenkins.ADMINISTER) {
    l.side_panel {
        l.tasks {
            l.task(icon: "icon-up icon-md", href: rootURL + '/', title: _("Back to Dashboard"))
            l.task(icon: "icon-gear icon-md", href: "${rootURL}/manage", title: _("Manage Jenkins"))
        }
    }
    def title = _("title", my.shortName)
    l.header(title:title)
    l.main_panel {
        h1 {
            text(title)
        }
        p { raw _("msg",my.shortName) }
        f.form(method:"post",action:"doUninstall") {
            f.submit(value:_("Yes"))
        }
    }
}
