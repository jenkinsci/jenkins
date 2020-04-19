
//        <l:task href="." icon="icon-package icon-md" title="${%Install Updates}">
//        <l:task href="available" icon="icon-package icon-md" title="${%Browse Plugins}"/>
//        <l:task href="status" icon="icon-package icon-md" title="${%Update Status}"/>
//        </l:task>
//      <l:task href="${rootURL}/pluginManager/" icon="icon-plugin icon-lg" title="${%Manage Plugins}"/>
//        </l:tasks>
//  </l:side-panel>
//        </j:jelly>


package hudson.model.UpdateCenter

import jenkins.model.Jenkins

l=namespace(lib.LayoutTagLib)

l.header()
l.side_panel {
    l.tasks {
        l.task(icon:"icon-up icon-md", href:rootURL+'/', title:_("Back to Dashboard"))
        l.task(icon:"icon-gear2 icon-md", href:"${rootURL}/manage", title:_("Manage Jenkins"))

        l.task(icon:"icon-package icon-md", href:".", title:_("Install Updates"))
        l.task(icon: "icon-search icon-md", href: "available", title: _("Browse Plugins"))
        if (!Jenkins.get().updateCenter.jobs.isEmpty()) {
            l.task(icon: "icon-up icon-md", href: "status", title: _("Install Status"))
        }
        l.task(icon:"icon-plugin icon-md", href:"${rootURL}/pluginManager/", title:_("Manage Plugins"))
    }
}
