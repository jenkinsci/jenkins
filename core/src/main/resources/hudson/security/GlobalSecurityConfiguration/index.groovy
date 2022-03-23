package hudson.security.GlobalSecurityConfiguration

import hudson.security.SecurityRealm
import hudson.markup.MarkupFormatterDescriptor
import hudson.security.AuthorizationStrategy
import jenkins.AgentProtocol
import hudson.Functions
import hudson.model.Descriptor

def f=namespace(lib.FormTagLib)
def l=namespace(lib.LayoutTagLib)
def st=namespace("jelly:stapler")

l.layout(permission:app.SYSTEM_READ, title:my.displayName, cssclass:request.getParameter('decorate')) {
    l.app_bar(title: my.displayName)

    l.main_panel {
        set("readOnlyMode", !app.hasPermission(app.ADMINISTER))

        p()
        div(class:"behavior-loading") {
            l.spinner(text: gettext("LOADING"))
        }
        f.form(method:"post",name:"config",action:"configure", class: "jenkins-form") {
            set("instance",my)
            set("descriptor", my.descriptor)

            f.section(title:gettext("Authentication")) {
                f.entry() {
                    f.checkbox(title:gettext("Disable remember me"), field: "disableRememberMe")
                }

                f.descriptorRadioList(title:gettext("Security Realm"), varName:"realm", instance:app.securityRealm, descriptors: h.filterDescriptors(app, SecurityRealm.all()))

                f.descriptorRadioList(title:gettext("Authorization"), varName:"authorization", instance:app.authorizationStrategy, descriptors:h.filterDescriptors(app, AuthorizationStrategy.all()))
            }

            f.section(title: gettext("Markup Formatter")) {
                f.dropdownDescriptorSelector(title:gettext("Markup Formatter"),descriptors: MarkupFormatterDescriptor.all(), field: 'markupFormatter')
            }

            f.section(title: gettext("Agents")) {
                f.entry(title: gettext("TCP port for inbound agents"), field: "slaveAgentPort") {
                    if (my.slaveAgentPortEnforced) {
                        if (my.slaveAgentPort == -1) {
                            text(gettext("slaveAgentPortEnforcedDisabled"))
                        } else if (my.slaveAgentPort == 0) {
                            text(gettext("slaveAgentPortEnforcedRandom"))
                        } else {
                            text(gettext("slaveAgentPortEnforced", my.slaveAgentPort))
                        }
                    } else {
                        f.serverTcpPort()
                    }
                }
                f.advanced(title: gettext("Agent protocols"), align:"left") {
                    f.entry(title: gettext("Agent protocols")) {
                        def agentProtocols = my.agentProtocols
                        div() {
                            for (AgentProtocol p : AgentProtocol.all()) {
                                if (p.name != null && !p.required) {
                                    f.block() {
                                        f.checkbox(name: "agentProtocol",
                                                title: p.displayName,
                                                checked: agentProtocols.contains(p.name),
                                                json: p.name)
                                    }
                                    div(class: "tr") {
                                        div(class:"setting-description"){
                                            st.include(from:p, page: "description", optional:true)
                                            if (p.deprecated) {
                                              br()
                                              text(b(gettext("Deprecated. ")))
                                              st.include(from:p, page: "deprecationCause", optional:true)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Functions.getSortedDescriptorsForGlobalConfigByDescriptor(my.FILTER).each { Descriptor descriptor ->
                set("descriptor",descriptor)
                set("instance",descriptor)
                f.rowSet(name:descriptor.jsonSafeClassName) {
                    st.include(from:descriptor, page:descriptor.globalConfigPage)
                }
            }

            l.isAdmin() {
                f.bottomButtonBar {
                    f.submit(value: gettext("Save"))
                    f.apply()
                }
            }
        }

        l.isAdmin() {
            st.adjunct(includes: "lib.form.confirm")
        }
    }
}

