package jenkins.widgets.ExecutorsWidget

def t = namespace(lib.JenkinsTagLib.class)

t.executors(computers:view.computers, it:view)
