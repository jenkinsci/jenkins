package hudson.views.MyViewsTabBar.GlobalConfigurationImpl

import hudson.views.MyViewsTabBar

def f=namespace(lib.FormTagLib)

def all = MyViewsTabBar.all()
if (all.size()>1) {
    f.dropdownDescriptorSelector(title:gettext("My Views Tab Bar"),field:"myViewsTabBar")
}
