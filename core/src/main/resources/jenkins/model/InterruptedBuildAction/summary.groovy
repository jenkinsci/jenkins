package jenkins.model.InterruptedBuildAction

def t = namespace(lib.JenkinsTagLib.class)

t.summary(icon:"symbol-close-circle") {
    my.causes.each { c ->
        p { include(c,"summary") }
    }
}
