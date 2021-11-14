package jenkins.tools.GlobalToolConfiguration

import hudson.Functions
import hudson.model.Descriptor

def f=namespace(lib.FormTagLib)
def l=namespace(lib.LayoutTagLib)
def st=namespace("jelly:stapler")

l.layout(permission:app.SYSTEM_READ, title:my.displayName) {
    l.side_panel {
        l.tasks {
            l.task(icon:"icon-up icon-md", href:rootURL+'/', title:_("Back to Dashboard"))
            l.task(icon:"icon-gear icon-md", href:"${rootURL}/manage", title:_("Manage Jenkins"))
        }
    }
    set("readOnlyMode", !app.hasPermission(app.ADMINISTER))
    l.main_panel {
        div(class:"jenkins-app-bar") {
            div(class: "jenkins-app-bar__content") {
                h1 {
                    text(my.displayName)
                }
            }
        }

        p()
        div(class:"behavior-loading") {
            l.spinner(text: _("LOADING"))
        }

        f.form(method:"post",name:"config",action:"configure") {
            Functions.getSortedDescriptorsForGlobalConfigByDescriptor(my.FILTER).each { Descriptor descriptor ->
                set("descriptor",descriptor)
                set("instance",descriptor)

                div(class: "row-set-start row-group-start tr", style: "display:none", name: descriptor.jsonSafeClassName)
                div(name:descriptor.jsonSafeClassName, class: "jenkins-section") {
                    st.include(from:descriptor, page:descriptor.globalConfigPage)
                }
                div(class: "row-set-end row-group-end tr")
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
