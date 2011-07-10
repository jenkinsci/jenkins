package hudson.model.View;

t=namespace(lib.JenkinsTagLib)
st=namespace("jelly:stapler")

if (items.isEmpty()) {
    if (app.items.size() != 0) {
        context.variables["views"] = my.owner.views;
        context.variables["currentView"] = my;
        st.include(page: "viewTabs.jelly", it: my.owner.viewsTabBar);
    }
    st.include(page: "noJob.jelly");
} else {
    t.projectView(jobs: items, jobBaseUrl: "", showViewTabs: true, columnExtensions: my.columns, indenter: my.indenter) {
        context.variables["currentView"] = my;
        context.variables["views"] = my.owner.views;
        if (my.owner.class == hudson.model.MyViewsProperty.class) {
            st.include(page: "myViewTabs.jelly", 'it': my.owner?.myViewsTabBar);
        } else {
            st.include(page: "viewTabs.jelly", 'it': my.owner.viewsTabBar);
        }
    }
}