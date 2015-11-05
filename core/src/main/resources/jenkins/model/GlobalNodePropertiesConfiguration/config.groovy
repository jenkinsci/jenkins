package jenkins.model.GlobalNodePropertiesConfiguration

import hudson.Functions

def f=namespace(lib.FormTagLib)

f.descriptorList(title:_("Global properties"),  name:"globalNodeProperties",
        instances: app.globalNodeProperties, descriptors: Functions.getGlobalNodePropertyDescriptors());
