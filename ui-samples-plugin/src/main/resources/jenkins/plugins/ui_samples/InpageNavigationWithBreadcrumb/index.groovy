package jenkins.plugins.ui_samples.InpageNavigationWithBreadcrumb;

import lib.JenkinsTagLib

def st=namespace("jelly:stapler")

t=namespace(JenkinsTagLib.class)

namespace("/lib/samples").sample(title:_("In-page navigation via breadcrumb")) {
    raw(_("blurb"))

    script """
    Event.observe(window,"load",function(){
      var menu = new breadcrumbs.ContextMenu();
      menu.add('#section1',rootURL+"/images/24x24/gear.png","Section 1")
      menu.add('#section2',rootURL+"/images/24x24/gear.png","Section 2")
      breadcrumbs.attachMenu('id-of-breadcrumb-item',menu);
    });
"""

}
