package hudson.security.GlobalSecurityConfiguration

import hudson.security.SecurityRealm
import hudson.markup.MarkupFormatterDescriptor
import hudson.security.AuthorizationStrategy
import jenkins.model.GlobalConfiguration
import hudson.Functions
import hudson.model.Descriptor

def f=namespace(lib.FormTagLib)
def l=namespace(lib.LayoutTagLib)
def st=namespace("jelly:stapler")

l.layout(norefresh:true, permission:app.ADMINISTER, title:my.displayName) {
    l.main_panel {
        h1 {
            l.icon(class: 'icon-secure icon-xlg')
            text(my.displayName)
        }

        p()
        div(class:"behavior-loading", _("LOADING"))
        f.form(method:"post",name:"config",action:"configure") {
            set("instance",my);
            set("descriptor", my.descriptor);

            f.optionalBlock( field:"useSecurity", title:_("Enable security"), checked:app.useSecurity) {
                f.entry (title:_("TCP port for JNLP slave agents"), field:"slaveAgentPort") {
                    f.serverTcpPort()
                }

                f.entry (title:_("Disable remember me"), field: "disableRememberMe") {
                    f.checkbox()
                }

                f.entry(title:_("Access Control")) {
                    table(style:"width:100%") {
                        f.descriptorRadioList(title:_("Security Realm"),varName:"realm",         instance:app.securityRealm,         descriptors:SecurityRealm.all())
                        f.descriptorRadioList(title:_("Authorization"), varName:"authorization", instance:app.authorizationStrategy, descriptors:AuthorizationStrategy.all())
                    }
                }
            }

            f.dropdownDescriptorSelector(title:_("Markup Formatter"),descriptors: MarkupFormatterDescriptor.all(), field: 'markupFormatter')

            Functions.getSortedDescriptorsForGlobalConfig(my.FILTER).each { Descriptor descriptor ->
                set("descriptor",descriptor)
                set("instance",descriptor)
                f.rowSet(name:descriptor.jsonSafeClassName) {
                    st.include(from:descriptor, page:descriptor.globalConfigPage)
                }
            }

            f.bottomButtonBar {
                f.submit(value:_("Save"))
                f.apply()
            }
        }

        st.adjunct(includes: "lib.form.confirm")
    }
}

