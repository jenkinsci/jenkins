package hudson.security.GlobalSecurityConfiguration

import hudson.security.SecurityRealm
import hudson.security.AuthorizationStrategy

def f=namespace(lib.FormTagLib)

f.optionalBlock( field:"useSecurity", title:_("Enable security"), checked:app.useSecurity) {
    f.entry (title:_("TCP port for JNLP slave agents"), field:"slaveAgentPort") {
        f.serverTcpPort()
    }

    f.dropdownDescriptorSelector(title:_("Markup Formatter"),field:"markupFormatter")

    f.entry(title:_("Access Control")) {
        table(style:"width:100%") {
            f.descriptorRadioList(title:_("Security Realm"),varName:"realm",         instance:app.securityRealm,         descriptors:SecurityRealm.all())
            f.descriptorRadioList(title:_("Authorization"), varName:"authorization", instance:app.authorizationStrategy, descriptors:AuthorizationStrategy.all())
        }
    }
}
