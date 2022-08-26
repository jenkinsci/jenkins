package jenkins.model.GlobalComputerRetentionCheckIntervalConfiguration

def f=namespace(lib.FormTagLib)

f.entry(title:_("Computer Retention Check Interval"), field:"computerRetentionCheckInterval") {
    f.number(clazz:"number", min:1)
}
