package hudson.model.View;

t=namespace(lib.JenkinsTagLib)
st=namespace("jelly:stapler")

if (items.isEmpty()) {
    if (app.items.size() != 0) {
        set("views",my.owner.views);
        set("currentView",my);
        include(my.owner.viewsTabBar, "viewTabs");
    }
    include(my,"noJob.jelly");
} else {
    t.projectView(jobs: items, showViewTabs: true, columnExtensions: my.columns, indenter: my.indenter, itemGroup: my.owner.itemGroup) {
        set("views",my.owner.views);
        set("currentView",my);
        if (my.owner.class == hudson.model.MyViewsProperty.class) {
            include(my.owner?.myViewsTabBar, "myViewTabs");
        } else {
            include(my.owner.viewsTabBar,"viewTabs");
        }
    }
}