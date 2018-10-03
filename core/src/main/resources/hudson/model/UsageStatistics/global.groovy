package hudson.model.UsageStatistics;

def f=namespace(lib.FormTagLib)

f.section(title: _("Usage Statistics")) {
    f.entry(field: "usageStatisticsCollected") {
        f.checkbox(title: _("statsBlurb"))
    }
}
