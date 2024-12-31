import hudson.agents.AgentComputer

def fmt = new java.text.DecimalFormat("0.0")
if (my instanceof AgentComputer) {
    AgentComputer c = my

    table(class: 'jenkins-table') {
        thead {
            tr {
                th { text(_('Loading Type')) }
                th { text(_('Time (s)')) }
                th { text(_('Count')) }
            }
        }
        tr {
            td _('Classes')
            td {text(fmt.format(c.classLoadingTime / 1000000000))}
            td {
                text(c.classLoadingCount)
                def classLoadingPrefetchCacheCount = c.classLoadingPrefetchCacheCount
                if (classLoadingPrefetchCacheCount != -1) {
                    text(_(' (prefetch cache: '))
                    text(classLoadingPrefetchCacheCount)
                    text(_(')'))
                }
            }
        }
        tr {
            td _('Resources')
            td {text(fmt.format(c.resourceLoadingTime / 1000000000))}
            td {text(c.resourceLoadingCount)}
        }
    }
}
