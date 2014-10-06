package jenkins.mvn.GlobalMavenConfig;

def f = namespace(lib.FormTagLib)

f.section(title:_("Maven Configuration")) {
    f.dropdownDescriptorSelector(title:_("Default settings provider"), field:"settingsProvider")
    f.dropdownDescriptorSelector(title:_("Default global settings provider"), field:"globalSettingsProvider")
}
