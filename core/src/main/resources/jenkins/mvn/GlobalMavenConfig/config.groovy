package jenkins.mvn.GlobalMavenConfig

def f = namespace(lib.FormTagLib)

f.section(title:_("Maven Configuration")) {
    div(class: "jenkins-form-item") {
        f.dropdownDescriptorSelector(title:_("Default settings provider"), field:"settingsProvider")
    }
    div(class: "jenkins-form-item") {
        f.dropdownDescriptorSelector(title:_("Default global settings provider"), field:"globalSettingsProvider")
    }
}
