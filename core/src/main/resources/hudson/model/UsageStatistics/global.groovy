package hudson.model.UsageStatistics;

def f=namespace(lib.FormTagLib)

f.optionalBlock( field:"usageStatisticsCollected", checked:app.usageStatisticsCollected, title:_("statsBlurb"))
