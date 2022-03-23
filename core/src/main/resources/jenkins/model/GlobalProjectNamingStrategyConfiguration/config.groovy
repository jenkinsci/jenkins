package jenkins.model.GlobalProjectNamingStrategyConfiguration

import jenkins.model.ProjectNamingStrategy

def f=namespace(lib.FormTagLib)

div(class: "jenkins-form-item") {
    f.optionalBlock( field:"useProjectNamingStrategy", title:gettext("useNamingStrategy"), checked:app.useProjectNamingStrategy) {

        f.entry(title:gettext("namingStrategyTitle")) {
            div(style:"width:100%") {
                f.descriptorRadioList(title:gettext("strategy"), varName:"namingStrategy", instance:app.projectNamingStrategy, descriptors:ProjectNamingStrategy.all())
            }
        }

    }
}
