package jenkins.model.GlobalNodePropertiesConfiguration

import jenkins.model.Jenkins
import hudson.Functions

def f=namespace(lib.FormTagLib)

if (Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
    f.descriptorList(title:_("Global properties"),  name:"globalNodeProperties",
            instances: app.globalNodeProperties, descriptors: Functions.getGlobalNodePropertyDescriptors())
}