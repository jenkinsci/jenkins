package hudson.model.UsageStatistics

def f=namespace(lib.FormTagLib)

f.section(title: _("Usage Statistics")) {
    f.optionalBlock(field: "usageStatisticsCollected", checked: app.usageStatisticsCollected, title: _("statsBlurb"))
}
