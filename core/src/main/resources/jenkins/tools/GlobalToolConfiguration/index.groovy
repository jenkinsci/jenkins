package jenkins.tools.GlobalToolConfiguration

import hudson.Functions
import hudson.model.Descriptor
import jenkins.model.experimentalflags.UserExperimentalFlag

def f=namespace(lib.FormTagLib)
def l=namespace(lib.LayoutTagLib)
def st=namespace("jelly:stapler")
def newManageJenkins = UserExperimentalFlag.getFlagValueForCurrentUser("jenkins.model.experimentalflags.NewManageJenkinsUserExperimentalFlag")

l.'settings-subpage'(permission: app.SYSTEM_READ) {
    set("readOnlyMode", !app.hasPermission(app.ADMINISTER))

    l.skeleton()

    f.form(method:"post",name:"config",action:"configure", class: "jenkins-form") {
        Functions.getSortedDescriptorsForGlobalConfigByDescriptor(my.FILTER).each { Descriptor descriptor ->
            set("descriptor",descriptor)
            set("instance",descriptor)
            f.rowSet(name:descriptor.jsonSafeClassName) {
                st.include(from:descriptor, page:descriptor.globalConfigPage)
            }
        }

        l.isAdmin() {
            if (newManageJenkins) {
                f.saveBar()
            } else {
                f.saveApplyBar()
            }
        }
    }
}
