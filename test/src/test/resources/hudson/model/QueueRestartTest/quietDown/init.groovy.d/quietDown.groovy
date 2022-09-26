import jenkins.model.Jenkins

// Start in a state that doesn't do any builds.
Jenkins.get().doQuietDown()
