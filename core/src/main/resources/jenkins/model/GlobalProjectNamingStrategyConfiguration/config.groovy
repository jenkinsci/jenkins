package jenkins.model.GlobalProjectNamingStrategyConfiguration

import jenkins.model.ProjectNamingStrategy

def f=namespace(lib.FormTagLib)

f.entry(title:_("namingStrategyTitle"), field:"projectNamingStrategy") {
    table(style:"width:100%") {
        f.descriptorRadioList(title:_("strategy"), varName:"namingStrategy",
                instance:instance.projectNamingStrategy, descriptors:ProjectNamingStrategy.all())
    }
}
