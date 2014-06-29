package hudson.model.EnvironmentContributor.EnvVarsHtml;
import hudson.model.EnvironmentContributor
import hudson.scm.SCM

def st = namespace("jelly:stapler")

st.contentType(value: "text/html;charset=UTF-8")

html {
    head {
        title(_("Available Environmental Variables"))
        style(type:"text/css", "dt { font-weight: bold; }")
    }
    body {
        p "The following variables are available to shell scripts"

        dl {
            EnvironmentContributor.all().each { e -> st.include(it:e, page:"buildEnv", optional:true) }

            // allow SCM classes to have buildEnv.groovy since SCM can contirbute environment variables
            SCM.all().each { e -> st.include(class:e.clazz, page:"buildEnv", optional:true) }
        }
    }
}
