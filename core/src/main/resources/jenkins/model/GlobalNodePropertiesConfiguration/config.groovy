package jenkins.model.GlobalNodePropertiesConfiguration

import hudson.Functions

def f=namespace(lib.FormTagLib)

f.entry(title:_("Global properties"), field: "globalNodeProperties") {
    f.descriptorList(descriptors: Functions.getGlobalNodePropertyDescriptors())
}
