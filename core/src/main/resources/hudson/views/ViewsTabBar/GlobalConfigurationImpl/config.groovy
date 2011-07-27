package hudson.views.ViewsTabBar.GlobalConfigurationImpl

import hudson.views.ViewsTabBar

def f=namespace(lib.FormTagLib)

def all = ViewsTabBar.all()
if (all.size()>1) {
    f.dropdownDescriptorSelector(title:_("Views Tab Bar"),field:"viewsTabBar")
}
