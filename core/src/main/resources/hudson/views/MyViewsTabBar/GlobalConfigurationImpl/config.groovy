package hudson.views.MyViewsTabBar.GlobalConfigurationImpl

import hudson.views.ViewsTabBar

def f=namespace(lib.FormTagLib)

def all = ViewsTabBar.all()
if (all.size()>1) {
    f.dropdownDescriptorSelector(title:_("My Views Tab Bar"),field:"viewsTabBar")
}
