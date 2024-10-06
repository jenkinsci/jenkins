package jenkins.tools.GlobalToolConfiguration

import hudson.Functions
import hudson.model.Descriptor

def f=namespace(lib.FormTagLib)
def l=namespace(lib.LayoutTagLib)
def st=namespace("jelly:stapler")

l.layout(permission:app.SYSTEM_READ, title:my.displayName, type:"one-column") {
    set("readOnlyMode", !app.hasPermission(app.ADMINISTER))
    l.main_panel {
        l.app_bar(title: my.displayName)

        div(class:"behavior-loading") {
            l.spinner(text: _("LOADING"))
        }

        f.form(method:"post",name:"config",action:"configure", class: "jenkins-form") {
            Functions.getSortedDescriptorsForGlobalConfigByDescriptor(my.FILTER).each { Descriptor descriptor ->
                set("descriptor",descriptor)
                set("instance",descriptor)
                f.rowSet(name:descriptor.jsonSafeClassName) {
                    st.include(from:descriptor, page:descriptor.globalConfigPage)
                }
            }

            l.isAdmin() {
                f.bottomButtonBar {
                    f.submit(value: _("Save"))
                    f.apply(value: _("Apply"))
                }
            }
        }

        l.isAdmin() {
            st.adjunct(includes: "lib.form.confirm")
        }
    }
}
