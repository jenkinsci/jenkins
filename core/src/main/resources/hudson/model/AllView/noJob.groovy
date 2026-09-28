package hudson.model.AllView

import hudson.model.Computer
import hudson.model.Item
import hudson.model.Job
import jenkins.model.Jenkins

def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")

def isTopLevelAllView = my.owner == Jenkins.get();
// Anything that can run a build counts as build capacity: executors on the built-in node, agents, or clouds.
def hasBuildCapacity = Jenkins.get().getNumExecutors() > 0 ||
        !Jenkins.get().clouds.isEmpty() ||
        !Jenkins.get().getNodes().isEmpty();
def canSetUpBuildCapacity = Jenkins.get().hasPermission(Computer.CREATE) && !hasBuildCapacity;
def hasAdministerJenkinsPermission = Jenkins.get().hasPermission(Jenkins.ADMINISTER);
def hasItemCreatePermission = my.owner.itemGroup.hasPermission(Item.CREATE);

div {

    div(class: "empty-state-block") {
        if (isTopLevelAllView) {
            if (canSetUpBuildCapacity || hasItemCreatePermission) {
                h1(_("Welcome to Jenkins!"))

                p(_("noJobDescription"))
                
                // Build capacity comes first: Without it, jobs cannot run at all. Once any kind of build capacity
                // exists, this whole section is done and disappears.
                if (canSetUpBuildCapacity) {
                    section(class: "empty-state-section") {
                        h2(_("setUpBuildCapacity"), class: "h4")

                        p(_("buildCapacityDescription"), class: "jenkins-description")

                        h3(_("distributedBuilds"), class: "h5")

                        ul(class: "empty-state-section-list") {
                            li(class: "content-block") {
                                a(href: "${rootURL}/computer/new", class: "content-block__link") {
                                    span(_("setUpAgent"))
                                    span(class: "trailing-icon") {
                                        l.icon(src: "symbol-computer")
                                    }
                                }
                            }

                            if (hasAdministerJenkinsPermission) {
                                li(class: "content-block") {
                                    a(href: "${rootURL}/cloud/", class: "content-block__link") {
                                        span(_("setUpCloud"))
                                        span(class: "trailing-icon") {
                                            l.icon(src: "symbol-cloud")
                                        }
                                    }
                                }
                            }

                            li(class: "content-block") {
                                a(href: "https://www.jenkins.io/redirect/distributed-builds",
                                        target: "_blank",
                                        class: "content-block__link") {
                                    span(_("learnMoreDistributedBuilds"))
                                    span(class: "trailing-icon") {
                                        l.icon(src: "symbol-help-circle")
                                    }
                                }
                            }
                        }

                        // The built-in node has no executors here, so offer it as a last resort to administrators
                        // who have no other infrastructure available.
                        if (hasAdministerJenkinsPermission) {
                            h3(_("configureBuiltInNode"), class: "h5 jenkins-!-margin-top-3")

                            p(_("builtInNodeDescription"), class: "jenkins-description")

                            st.adjunct(includes: "hudson.model.AllView.noJob")

                            ul(class: "empty-state-section-list") {
                                li(class: "content-block") {
                                    a(href: "${rootURL}/computer/(built-in)/addExecutor",
                                            class: "content-block__link empty-state-add-executor",
                                            "data-notification": _("addExecutorSuccess"),
                                            "data-failure": _("addExecutorFailure")) {
                                        span(_("addExecutor"))
                                        span(class: "trailing-icon") {
                                            l.icon(src: "symbol-add")
                                        }
                                    }
                                }

                                li(class: "content-block") {
                                    a(href: "https://www.jenkins.io/redirect/building-on-controller",
                                            target: "_blank",
                                            class: "content-block__link") {
                                        span(_("learnMoreBuiltInNode"))
                                        span(class: "trailing-icon") {
                                            l.icon(src: "symbol-help-circle")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                section(class: "empty-state-section") {
                    h2(_("startBuilding"), class: "h4")

                    ul(class: "empty-state-section-list") {
                        li(class: "content-block") {
                            a(href: "${rootURL}/newJob", class: "content-block__link") {
                                span(_("createJob"))
                                span(class: "trailing-icon") {
                                    l.icon(src: "symbol-add")
                                }
                            }
                        }
                    }
                }

            }
        } else if (hasItemCreatePermission) {
            // we're in a folder

            section(class: "empty-state-section") {
                h2(_("thisFolderIsEmpty"), class: "h4")

                ul(class: "empty-state-section-list") {
                    li(class: "content-block") {
                        a(href: "${rootURL}/newJob", class: "content-block__link") {
                            span(_("createJob"))
                            span(class: "trailing-icon") {
                                l.icon(src: "symbol-add")
                            }
                        }
                    }
                }
            }
        }

        // If the user is logged out
        if (h.isAnonymous() && !hasItemCreatePermission) {
            def canSignUp = app.securityRealm.allowsSignup()

            h1(_("Welcome to Jenkins!"))

            if (canSignUp) {
                p(_("anonymousDescriptionSignUpEnabled"))
            } else {
                p(_("anonymousDescription"))
            }

            section(class: "empty-state-section") {
                ul(class: "empty-state-section-list") {
                    li(class: "content-block") {
                        a(href: "${rootURL}/${app.securityRealm.loginUrl}?from=${request2.requestURI}",
                                class: "content-block__link") {
                            span(_("Log in to Jenkins"))
                            span(class: "trailing-icon") {
                                l.icon(
                                        class: "icon-md",
                                        src: "symbol-arrow-right")
                            }
                        }
                    }

                    if (canSignUp) {
                        li(class: "content-block") {
                            a(href: "${rootURL}/signup", class: "content-block__link") {
                                span(_("Sign up for Jenkins"))
                                span(class: "trailing-icon") {
                                    l.icon(
                                            class: "icon-md",
                                            src: "symbol-arrow-right")
                                }

                            }
                        }
                    }
                }
            }
        }
    }
}
