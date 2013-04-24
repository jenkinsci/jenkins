package jenkins.widgets.BuildQueueWidget;

def t = namespace(lib.JenkinsTagLib.class)

text(request.ancestors.last().object)

t.queue(items:view.approximateQueueItemsQuickly)