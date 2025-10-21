package jenkins.management.ShutdownNewLink

import jenkins.management.Messages

def f = namespace(lib.FormTagLib)
def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")

l.layout(permission: app.MANAGE, title: my.displayName, type: "one-column") {
    l.main_panel {
        h1 {
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
                f.submit(value: _(app.isQuietingDown()
                        ? Messages.ShutdownLink_ShutDownReason_update()
                        : Messages.ShutdownLink_DisplayName_prepare()),
                        clazz: "jenkins-!-destructive-color")
            }
        }

        if (app.isQuietingDown()) {
            f.form(method: "post", name: "cancelShutdown", action: "cancel") {
                f.bottomButtonBar {
                    f.submit(value: _(Messages.ShutdownLink_DisplayName_cancel()))
                }
            }
        }
    }
}
