package hudson.model.AllView

import hudson.model.Computer
import hudson.model.Item
import jenkins.model.Jenkins

def isTopLevelAllView = my.owner == Jenkins.get();
def canSetUpDistributedBuilds = Jenkins.get().hasPermission(Computer.CREATE) &&
        Jenkins.get().clouds.isEmpty() &&
        Jenkins.get().getNodes().isEmpty();
def hasAdministerJenkinsPermission = Jenkins.get().hasPermission(Jenkins.ADMINISTER);
def hasItemCreatePermission = my.owner.hasPermission(Item.CREATE);

div {
    h1(_("Welcome to Jenkins!"))

    if (isTopLevelAllView) {
        // we're a top-level 'All' view
        if (canSetUpDistributedBuilds) {
            div(class: 'call-to-action') {
                if (hasAdministerJenkinsPermission) {
                    raw(_("distributedBuildsWithCloud"))
                } else {
                    raw(_("distributedBuilds"))
                }
            }
        }
        if (hasItemCreatePermission) {
            div(class: 'call-to-action') {
                raw(_("newJob"))
            }
        }
    } else {
        // we're in a folder
        if (hasItemCreatePermission) {
            div(class: 'call-to-action') {
                raw(_("newJob"))
            }
        }
    }

    if (h.isAnonymous() && !hasItemCreatePermission) {
        div(class:'call-to-action') {
            raw(_("login", rootURL, app.securityRealm.loginUrl, request.requestURI))
            if (app.securityRealm.allowsSignup()) {
                text(" ") // TODO make this nicer
                raw(_("signup"))
            }
        }
    }
}
