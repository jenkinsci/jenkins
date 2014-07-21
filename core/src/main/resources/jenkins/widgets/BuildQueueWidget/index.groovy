package jenkins.widgets.BuildQueueWidget;

def t = namespace(lib.JenkinsTagLib.class)

t.queue(items:view.approximateQueueItemsQuickly, it:view, filtered:view.filterQueue)
