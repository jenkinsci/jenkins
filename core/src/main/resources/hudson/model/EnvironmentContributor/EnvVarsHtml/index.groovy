package hudson.model.EnvironmentContributor.EnvVarsHtml
import hudson.model.EnvironmentContributor
import hudson.scm.SCM

def st = namespace("jelly:stapler")
def l = namespace(lib.LayoutTagLib)

l.layout(title: _("Available Environmental Variables"), type: 'one-column') {
    l.main_panel {
        p _("blurb")

        dl {
            EnvironmentContributor.all().each { e -> st.include(it:e, page:"buildEnv", optional:true) }

            // allow SCM classes to have buildEnv.groovy since SCM can contribute environment variables
            SCM.all().each { e -> st.include(class:e.clazz, page:"buildEnv", optional:true) }
        }
    }
}
