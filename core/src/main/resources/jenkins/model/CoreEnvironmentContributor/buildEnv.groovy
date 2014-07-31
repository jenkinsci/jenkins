package jenkins.model.CoreEnvironmentContributor;

def l = namespace(lib.JenkinsTagLib)

// also advertises those contributed by Run.getCharacteristicEnvVars()
["BUILD_NUMBER","BUILD_ID","BUILD_DISPLAY_NAME","JOB_NAME","BUILD_TAG","EXECUTOR_NUMBER","NODE_NAME","NODE_LABELS","WORKSPACE","JENKINS_HOME","JENKINS_URL","BUILD_URL","JOB_URL"].each { name ->
    l.buildEnvVar(name:name) {
        raw(_("${name}.blurb"))
    }
}
