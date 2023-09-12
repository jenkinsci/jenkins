package hudson.model.UsageStatistics

import jenkins.security.FIPS140

def f=namespace(lib.FormTagLib)

f.section(title: _("Usage Statistics")) {
    if (FIPS140.useCompliantAlgorithms()) {
        span(class: "jenkins-not-applicable", _("disabledByFips"))
    } else {
        f.optionalBlock(field: "usageStatisticsCollected", checked: app.usageStatisticsCollected, title: _("statsBlurb"))
    }
}
