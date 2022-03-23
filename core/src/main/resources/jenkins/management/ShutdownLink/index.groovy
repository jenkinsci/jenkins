package jenkins.management.ShutdownNewLink

import jenkins.management.Messages

def f = namespace(lib.FormTagLib)
def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")

l.layout(norefresh: true, permission: app.MANAGE, title: my.displayName) {
    l.side_panel {
        l.tasks {
            l.task(icon: "icon-up icon-md", href: rootURL + '/', title: gettext("Back to Dashboard"))
            l.task(icon: "symbol-settings", href: "${rootURL}/manage", title: gettext("Manage Jenkins"))
        }
    }
    l.main_panel {
        h1 {
            l.icon(class: 'icon-system-log-out icon-xlg')
            text(Messages.ShutdownLink_DisplayName_prepare())
        }

        p {
            text(my.description)
        }

        f.form(method: "post", name: "prepareShutdown", action: "prepare") {
            f.entry(title: Messages.ShutdownLink_ShutDownReason_title()) {
                f.textbox(name: "parameter.shutdownReason", value: app.quietDownReason ?: null)
            }


            f.bottomButtonBar {
                f.submit(value: gettext(app.isQuietingDown()
                        ? Messages.ShutdownLink_ShutDownReason_update()
                        : Messages.ShutdownLink_DisplayName_prepare()))
            }
        }

        if (app.isQuietingDown()) {
            f.form(method: "post", name: "cancelShutdown", action: "cancel") {
                f.bottomButtonBar {
                    f.submit(value: gettext(Messages.ShutdownLink_DisplayName_cancel()))
                }
            }
        }
    }
}
