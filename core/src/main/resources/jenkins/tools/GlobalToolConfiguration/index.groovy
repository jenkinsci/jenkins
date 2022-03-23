package jenkins.tools.GlobalToolConfiguration

import hudson.Functions
import hudson.model.Descriptor

def f=namespace(lib.FormTagLib)
def l=namespace(lib.LayoutTagLib)
def st=namespace("jelly:stapler")

l.layout(permission:app.SYSTEM_READ, title:my.displayName) {
    l.side_panel {
        l.tasks {
            l.task(icon:"icon-up icon-md", href:rootURL+'/', title:gettext("Back to Dashboard"))
            l.task(icon:"symbol-settings", href:"${rootURL}/manage", title:gettext("Manage Jenkins"))
        }
    }
    l.app_bar(title: my.displayName)
    set("readOnlyMode", !app.hasPermission(app.ADMINISTER))
    l.main_panel {
        div(class:"behavior-loading") {
            l.spinner(text: gettext("LOADING"))
        }

        f.form(method:"post",name:"config",action:"configure", class: "jenkins-form") {
            Functions.getSortedDescriptorsForGlobalConfigByDescriptor(my.FILTER).each { Descriptor descriptor ->
                set("descriptor",descriptor)
                set("instance",descriptor)
                f.rowSet(name:descriptor.jsonSafeClassName, class: "jenkins-!-margin-bottom-0") {
                    st.include(from:descriptor, page:descriptor.globalConfigPage)
                }
            }

            l.isAdmin() {
                f.bottomButtonBar {
                    f.submit(value: gettext("Save"))
                    f.apply(value: gettext("Apply"))
                }
            }
        }

        l.isAdmin() {
            st.adjunct(includes: "lib.form.confirm")
        }
    }
}
