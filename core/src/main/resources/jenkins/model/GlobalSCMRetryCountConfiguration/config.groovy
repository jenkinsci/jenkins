package jenkins.model.GlobalSCMRetryCountConfiguration

def f=namespace(lib.FormTagLib)

f.entry(title:_("SCM checkout retry count"), field:"scmCheckoutRetryCount") {
    f.number(clazz:"number", min:0)
}
