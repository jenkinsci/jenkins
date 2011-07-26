package hudson.security.csrf.GlobalCrumbIssuerConfiguration

import hudson.security.SecurityRealm
import hudson.security.AuthorizationStrategy

def f=namespace(lib.FormTagLib)

f.optionalBlock( field:"useSecurity", title:_("Enable security"), checked:app.useSecurity) {
    f.entry (title:_("TCP port for JNLP slave agents")) {

        int port = app.slaveAgentPort

        f.radio(name:"slaveAgentPortType", value:"fixed", id:"sat.fixed",
                chcked:port>0, onclick:"\$('sat.port').disabled=false")
        label("for":"sat.fixed", _$("Fixed"))
        text(" : ")
        input(type:"text", "class":"number", name:"slaveAgentPort", id:"sat.port",
                value: port>0 ? port : null, disabled: port>0 ? null : "true" )

        raw("&nbsp;") ////////////////////////////

        f.radio(name:"slaveAgentPortType", value:"random", id:"sat.random",
                checked:port==0, onclick:"\$('sat.port').disabled=true")
        label("for":"sat.random", _$("Random"))

        raw("&nbsp;") ////////////////////////////

        f.radio(name:"slaveAgentPortType", value:"disable", id:"sat.disabled",
                checked:port==-1, onclick:"\$('sat.port').disabled=true")
        label("for":"sat.random", _$("Random"))

//                 checked="${it.useSecurity}" help="/help/system-config/enableSecurity.html">
//      help="/help/system-config/master-slave/slave-agent-port.html">
/*
    <input type="text" class="number" name="slaveAgentPort" id="sat.port"
       value="${it.slaveAgentPort gt 0 ? it.slaveAgentPort : null}"
       disabled="${it.slaveAgentPort gt 0 ? null : 'true'}"/>

    <st:nbsp />

    <f:radio name="slaveAgentPortType" value="random" id="sat.random"
             checked="${it.slaveAgentPort==0}" onclick="$('sat.port').disabled=true" />
    <label for="sat.random">${%Random}</label>

    <st:nbsp />

    <f:radio name="slaveAgentPortType" value="disable" id="sat.disabled"
             checked="${it.slaveAgentPort==-1}" onclick="$('sat.port').disabled=true" />
    <label for="sat.disabled">${%Disable}</label>
  </f:entry>
*/
    }

    f.dropdownDescriptorSelector(title:_("Markup Formatter"),field:"markupFormatter")

    f.entry(title:_("Access Control")) {
        table(style:"width:100%") {
            f.descriptorRadioList(title:_("Security Realm"),varName:"realm",         instance:app.securityRealm,         descriptors:SecurityRealm.all())
            f.descriptorRadioList(title:_("Authorization"), varName:"authorization", instance:app.authorizationStrategy, descriptors:AuthorizationStrategy.all())
        }
    }
}

/*
  <f:dropdownDescriptorSelector title="${%Markup Formatter}" field="markupFormatter" />

  <f:entry title="${%Access Control}">
    <table style="width:100%">
      <f:descriptorRadioList title="${%Security Realm}" varName="realm"
                             instance="${it.securityRealm}"
                             descriptors="${h.securityRealmDescriptors}"/>
      <f:descriptorRadioList title="${%Authorization}" varName="authorization"
                             instance="${it.authorizationStrategy}"
                             descriptors="${h.authorizationStrategyDescriptors}"/>
    </table>
  </f:entry>
</f:optionalBlock>
*/