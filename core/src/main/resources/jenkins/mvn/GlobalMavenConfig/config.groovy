package jenkins.mvn.GlobalMavenConfig

def f = namespace(lib.FormTagLib)

f.section(title:gettext("Maven Configuration")) {
  f.dropdownDescriptorSelector(title:gettext("Default settings provider"), field:"settingsProvider")
  f.dropdownDescriptorSelector(title:gettext("Default global settings provider"), field:"globalSettingsProvider")
}
