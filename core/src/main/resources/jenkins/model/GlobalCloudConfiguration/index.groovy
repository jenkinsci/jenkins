package jenkins.model.GlobalCloudConfiguration

import hudson.slaves.Cloud


def f = namespace(lib.FormTagLib)
def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")

l.layout(norefresh:true, permission:app.ADMINISTER, title:my.displayName) {
    l.side_panel {
        l.tasks {
            l.task(icon:"icon-up icon-md", href:rootURL+'/', title:_("Back to Dashboard"))
            l.task(icon:"icon-gear2 icon-md", href:"${rootURL}/computer/", title:_("Manage Nodes"))
        }
    }
    l.main_panel {
        h1 {
            l.icon(class: 'icon-health-40to59 icon-xlg')
            // TODO more appropriate icon
            text(my.displayName)
        }

        def clouds = Cloud.all()

        if (!clouds.isEmpty()) {
            p()
            div(class:"behavior-loading", _("LOADING"))

            f.form(method:"post",name:"config",action:"configure") {
                f.block {
                    f.hetero_list(name:"cloud", hasHeader:true, descriptors:Cloud.all(), items:app.clouds,
                            addCaption:_("Add a new cloud"), deleteCaption:_("Delete cloud"))
                }

                f.bottomButtonBar {
                    f.submit(value:_("Save"))
                    f.apply(value:_("Apply"))
                }
            }
            st.adjunct(includes: "lib.form.confirm")
        } else {
            p {
                _("There are no cloud implementations for dynamically allocated agents installed.")
                a(href: rootURL + "/pluginManager/available") {
                    _("Go to plugin manager.")
                }
            }
        }
    }
}
