package jenkins.model.GlobalQuietPeriodConfiguration

def f=namespace(lib.FormTagLib)

f.entry(title:_("Quiet period"), field:"quietPeriod") {
    f.number(clazz:"number", min:0)
}
