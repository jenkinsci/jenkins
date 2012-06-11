import hudson.model.EnvironmentContributor

def st = namespace("jelly:stapler")

html {
    head {
        title(_("Available Environmental Variables"))
        style(type:"text/css", "dt { font-weight: bold; }")
    }
    body {
        p "The following variables are available to shell scripts"

        dl {
            EnvironmentContributor.all().each { e -> st.include(it:e, page:"buildEnv", optional:true) }
        }
    }
}
