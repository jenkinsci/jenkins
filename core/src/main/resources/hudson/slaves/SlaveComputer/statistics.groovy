h1(_('Remote Class Loader Statistics'))
def fmt = new java.text.DecimalFormat("0.0")
def right = 'text-align: right'
table(class: 'bigtable') {
    tr {
        th _('Loading Type')
        th _('Time (s)')
        th _('Count')
    }
    tr {
        td _('Classes')
        td(style: right) {text(fmt.format(my.classLoadingTime / 1000000000))}
        td(style: right) {
            text(my.classLoadingCount)
            def classLoadingPrefetchCacheCount = my.classLoadingPrefetchCacheCount;
            if (classLoadingPrefetchCacheCount != -1) {
                text(_(' (prefetch cache: '))
                text(classLoadingPrefetchCacheCount)
                text(_(')'))
            }
        }
    }
    tr {
        td _('Resources')
        td(style: right) {text(fmt.format(my.resourceLoadingTime / 1000000000))}
        td(style: right) {text(my.resourceLoadingCount)}
    }
}
