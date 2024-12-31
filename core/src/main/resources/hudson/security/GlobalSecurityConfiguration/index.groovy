package hudson.security.GlobalSecurityConfiguration

import hudson.security.SecurityRealm
import hudson.markup.MarkupFormatterDescriptor
import hudson.security.AuthorizationStrategy
import hudson.Functions
import hudson.model.Descriptor

def f=namespace(lib.FormTagLib)
def l=namespace(lib.LayoutTagLib)
def st=namespace("jelly:stapler")

l.layout(permission:app.SYSTEM_READ, title:my.displayName, cssclass:request.getParameter('decorate'), type:"one-column") {
    l.main_panel {
        l.app_bar(title: my.displayName)

        set("readOnlyMode", !app.hasPermission(app.ADMINISTER))

        p()
        div(class:"behavior-loading") {
            l.spinner(text: _("LOADING"))
        }
        f.form(method:"post", name:"config", action:"configure", class: "jenkins-form") {
            set("instance", my)
            set("descriptor", my.descriptor)

            f.section(title:_("Authentication")) {
                f.entry(help: '/descriptor/hudson.security.GlobalSecurityConfiguration/help/disableRememberMe') {
                    f.checkbox(title:_("Disable remember me"), field: "disableRememberMe")
                }
                f.dropdownDescriptorSelector(title: _("Security Realm"), field: 'securityRealm', descriptors: h.filterDescriptors(app, SecurityRealm.all()))
                f.dropdownDescriptorSelector(title: _("Authorization"), field: 'authorizationStrategy', descriptors: h.filterDescriptors(app, AuthorizationStrategy.all()))
            }

            f.section(title: _("Markup Formatter")) {
                f.dropdownDescriptorSelector(title:_("Markup Formatter"), descriptors: MarkupFormatterDescriptor.all(), field: 'markupFormatter')
            }

            f.section(title: _("Agents")) {
                f.entry(title: _("TCP port for inbound agents"), field: "AgentPort") {
                    if (my.AgentPortEnforced) {
                        if (my.AgentPort == -1) {
                            text(_("AgentPortEnforcedDisabled"))
                        } else if (my.AgentPort == 0) {
                            text(_("AgentPortEnforcedRandom"))
                        } else {
                            text(_("AgentPortEnforced", my.AgentPort))
                        }
                    } else {
                        f.serverTcpPort()
                    }
                }
            }

            Functions.getSortedDescriptorsForGlobalConfigByDescriptor(my.FILTER).each { Descriptor descriptor ->
                set("descriptor", descriptor)
                set("instance", descriptor)
                f.rowSet(name:descriptor.jsonSafeClassName) {
                    st.include(from:descriptor, page:descriptor.globalConfigPage)
                }
            }

            l.isAdmin() {
                f.bottomButtonBar {
                    f.submit(value: _("Save"))
                    f.apply()
                }
            }
        }

        l.isAdmin() {
            st.adjunct(includes: "lib.form.confirm")
        }
    }
}

