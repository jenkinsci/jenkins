package hudson.model.UsageStatistics

def f=namespace(lib.FormTagLib)

f.section(title: gettext("Usage Statistics")) {
    f.optionalBlock(field: "usageStatisticsCollected", checked: app.usageStatisticsCollected, title: gettext("statsBlurb"))
}
