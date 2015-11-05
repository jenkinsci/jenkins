import hudson.slaves.SlaveComputer

def fmt = new java.text.DecimalFormat("0.0")
def right = 'text-align: right'
if (my instanceof SlaveComputer) {
    SlaveComputer c = my;

    table(class: 'bigtable') {
        tr {
            th _('Loading Type')
            th _('Time (s)')
            th _('Count')
        }
        tr {
            td _('Classes')
            td(style: right) {text(fmt.format(c.classLoadingTime / 1000000000))}
            td(style: right) {
                text(c.classLoadingCount)
                def classLoadingPrefetchCacheCount = c.classLoadingPrefetchCacheCount;
                if (classLoadingPrefetchCacheCount != -1) {
                    text(_(' (prefetch cache: '))
                    text(classLoadingPrefetchCacheCount)
                    text(_(')'))
                }
            }
        }
        tr {
            td _('Resources')
            td(style: right) {text(fmt.format(c.resourceLoadingTime / 1000000000))}
            td(style: right) {text(c.resourceLoadingCount)}
        }
    }
}
