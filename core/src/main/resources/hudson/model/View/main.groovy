package hudson.model.View

import hudson.model.MyViewsProperty

t=namespace(lib.JenkinsTagLib)
st=namespace("jelly:stapler")

if (items == null) {
    p(_('broken'))
} else if (items.isEmpty()) {
    if (app.items.size() != 0) {
        set("views",my.owner.views)
        set("currentView",my)
        if (my.owner.class == MyViewsProperty.class) {
            include(my.owner?.viewsTabBar, "viewTabs")
        } else {
            include(my.owner.userViewsTabBar, "viewTabs")
        }
    }
    include(my,"noJob.jelly")
} else {
    t.projectView(jobs: items, showViewTabs: true, columnExtensions: my.columns, indenter: my.indenter, itemGroup: my.owner.itemGroup) {
        set("views",my.owner.views)
        set("currentView",my)
        if (my.owner.class == MyViewsProperty.class) {
            include(my.owner?.viewsTabBar, "viewTabs")
        } else {
            include(my.owner.userViewsTabBar,"viewTabs")
        }
    }
}
