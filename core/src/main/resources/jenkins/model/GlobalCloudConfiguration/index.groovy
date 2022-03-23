package jenkins.model.GlobalCloudConfiguration

import hudson.slaves.Cloud
import jenkins.model.Jenkins


def f = namespace(lib.FormTagLib)
def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")

l.layout(norefresh:true, permission:app.SYSTEM_READ, title:my.displayName) {
    set("readOnlyMode", !app.hasPermission(app.ADMINISTER))
    l.side_panel {
        l.tasks {
            l.task(icon:"icon-up icon-md", href:rootURL+'/', title:gettext("Back to Dashboard"))
            l.task(icon:"symbol-settings", href:"${rootURL}/computer/", title:gettext("Manage Nodes"))
        }
    }
    l.app_bar(title: my.displayName)
    l.main_panel {
        def clouds = Cloud.all()
        if (!clouds.isEmpty()) {
            p()
            div(class:"behavior-loading") {
                l.spinner(text: gettext("LOADING"))
            }

            f.form(method:"post",name:"config",action:"configure", class: "jenkins-form") {
                f.block {
                    if (app.clouds.size() == 0 && !h.hasPermission(app.ADMINISTER)) {
                        p(gettext("No clouds have been configured."))
                    }

                    f.hetero_list(name:"cloud", hasHeader:true, descriptors:Cloud.all(), items:app.clouds,
                            addCaption:gettext("Add a new cloud"), deleteCaption:gettext("Delete cloud"))
                }

                l.isAdmin {
                    f.bottomButtonBar {
                        f.submit(value: gettext("Save"))
                        f.apply(value: gettext("Apply"))
                    }
                }
            }
            l.isAdmin {
                st.adjunct(includes: "lib.form.confirm")
            }
        } else {
            String label = Jenkins.get().updateCenter.getCategoryDisplayName("cloud")

            p(gettext("There are no cloud implementations for dynamically allocated agents installed. "))
            a(href: rootURL + "/pluginManager/available?filter=" + URLEncoder.encode(label, "UTF-8"), gettext("Go to plugin manager."))
        }
    }
}
