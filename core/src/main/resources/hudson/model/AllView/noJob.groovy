package hudson.model.AllView

import hudson.model.Computer
import hudson.model.Item
import jenkins.model.Jenkins

div {
    h1(_("Welcome to Jenkins!"))

    if (my.owner == Jenkins.get()) {
        // we're a top-level 'All' view
        if (Jenkins.get().hasPermission(Computer.CREATE) && Jenkins.get().clouds.isEmpty() && Jenkins.get().getNodes().isEmpty()) {
            div(class: 'call-to-action') {
                if (Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    raw(_("distributedBuildsWithCloud"))
                } else {
                    raw(_("distributedBuilds"))
                }
            }
        }
        if (my.owner.hasPermission(Item.CREATE)) {
            div(class: 'call-to-action') {
                raw(_("newJob"))
            }
        }
    } else {
        // we're in a folder
        if (my.owner.hasPermission(Item.CREATE)) {
            div(class: 'call-to-action') {
                raw(_("newJob"))
            }
        }
    }

    if (h.isAnonymous() && !my.owner.hasPermission(Item.CREATE)) {
        div(class:'call-to-action') {
            raw(_("login", rootURL, app.securityRealm.loginUrl, request.requestURI))
            if (app.securityRealm.allowsSignup()) {
                text(" ") // TODO make this nicer
                raw(_("signup"))
            }
        }
    }
}
