package hudson.security.GlobalSecurityConfiguration

import hudson.security.SecurityRealm
import hudson.security.AuthorizationStrategy

def f=namespace(lib.FormTagLib)

f.optionalBlock( field:"useSecurity", title:_("Enable security"), checked:app.useSecurity) {
    f.entry (title:_("TCP port for JNLP slave agents"), field:"slaveAgentPort") {

        int port = app.slaveAgentPort

        f.radio(name:"slaveAgentPortType", value:"fixed", id:"sat.fixed",
                chcked:port>0, onclick:"\$('sat.port').disabled=false")
        label("for":"sat.fixed", _("Fixed"))
        text(" : ")
        input(type:"text", "class":"number", name:"slaveAgentPort", id:"sat.port",
                value: port>0 ? port : null, disabled: port>0 ? null : "true" )

        raw("&nbsp;") ////////////////////////////

        f.radio(name:"slaveAgentPortType", value:"random", id:"sat.random",
                checked:port==0, onclick:"\$('sat.port').disabled=true")
        label("for":"sat.random", _("Random"))

        raw("&nbsp;") ////////////////////////////

        f.radio(name:"slaveAgentPortType", value:"disable", id:"sat.disabled",
                checked:port==-1, onclick:"\$('sat.port').disabled=true")
        label("for":"sat.disabled", _("Disable"))
    }

    f.dropdownDescriptorSelector(title:_("Markup Formatter"),field:"markupFormatter")

    f.entry(title:_("Access Control")) {
        table(style:"width:100%") {
            f.descriptorRadioList(title:_("Security Realm"),varName:"realm",         instance:app.securityRealm,         descriptors:SecurityRealm.all())
            f.descriptorRadioList(title:_("Authorization"), varName:"authorization", instance:app.authorizationStrategy, descriptors:AuthorizationStrategy.all())
        }
    }
}
