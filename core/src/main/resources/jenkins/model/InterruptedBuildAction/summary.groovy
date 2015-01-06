package jenkins.model.InterruptedBuildAction

def t = namespace(lib.JenkinsTagLib)

t.summary(icon:"orange-square.png") {
    my.causes.each { c ->
        p { include(c, "summary") }
    }
}
