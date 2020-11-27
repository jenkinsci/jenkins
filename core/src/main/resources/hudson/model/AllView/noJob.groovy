package hudson.model.AllView

import hudson.model.Computer
import hudson.model.Item
import jenkins.model.Jenkins

def l = namespace(lib.LayoutTagLib)

def isTopLevelAllView = my.owner == Jenkins.get();
def canSetUpDistributedBuilds = Jenkins.get().hasPermission(Computer.CREATE) &&
        Jenkins.get().clouds.isEmpty() &&
        Jenkins.get().getNodes().isEmpty();
def hasAdministerJenkinsPermission = Jenkins.get().hasPermission(Jenkins.ADMINISTER);
def hasItemCreatePermission = my.owner.hasPermission(Item.CREATE);

div {

    div(class: "empty-state-block") {
        if (isTopLevelAllView) {
            if (canSetUpDistributedBuilds || hasItemCreatePermission) {
                h1(_("Welcome to Jenkins!"))

                p(_("noJobDescription"))
                
                section(class: "empty-state-section") {
                    h2(_("startBuilding"), class: "h4")

                    ul(class: "empty-state-section-list") {
                        li(class: "content-block") {
                            a(href: "newJob", class: "content-block__link") {
                                span(_("createJob"))
                                span(class: "trailing-icon") {
                                    l.svgIcon(
                                            class: "icon-sm",
                                            href: "${resURL}/images/material-icons/svg-sprite-navigation-symbol.svg#ic_arrow_forward_24px")
                                }
                            }
                        }
                    }
                }

                if (canSetUpDistributedBuilds) {
                    section(class: "empty-state-section") {
                        h2(_("setUpDistributedBuilds"), class: "h4")
                        ul(class: "empty-state-section-list") {
                            li(class: "content-block") {
                                a(href: "computer/new", class: "content-block__link") {
                                    span(_("setUpAgent"))
                                    span(class: "trailing-icon") {
                                        l.svgIcon(
                                                class: "icon-sm",
                                                href: "${resURL}/images/material-icons/svg-sprite-navigation-symbol.svg#ic_arrow_forward_24px")
                                    }
                                }
                            }

                            if (hasAdministerJenkinsPermission) {
                                li(class: "content-block") {
                                    a(href: "configureClouds", class: "content-block__link") {
                                        span(_("setUpCloud"))
                                        span(class: "trailing-icon") {
                                            l.svgIcon(
                                                    class: "icon-sm",
                                                    href: "${resURL}/images/material-icons/svg-sprite-navigation-symbol.svg#ic_arrow_forward_24px")
                                        }
                                    }
                                }
                            }

                            li(class: "content-block") {
                                a(href: "https://jenkins.io/redirect/distributed-builds",
                                        target: "_blank",
                                        class: "content-block__link content-block__help-link") {
                                    span(_("learnMoreDistributedBuilds"))
                                    span(class: "trailing-icon") {
                                        l.svgIcon(
                                                class: "icon-sm",
                                                href: "${resURL}/images/material-icons/svg-sprite-content-symbol.svg#ic_link_24px")
                                    }
                                }
                            }
                        }
                    }
                }

            }
        } else if (hasItemCreatePermission) {
            // we're in a folder

            section(class: "empty-state-section") {
                h2("This folder is empty", class: "h4")

                ul(class: "empty-state-section-list") {
                    li(class: "content-block") {
                        a(href: "newJob", class: "content-block__link") {
                            span("Create a job")
                            span(class: "trailing-icon") {
                                l.svgIcon(
                                        class: "icon-sm",
                                        href: "${resURL}/images/material-icons/svg-sprite-navigation-symbol.svg#ic_arrow_forward_24px")
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

            p(_("anonymousDescription"))

            section(class: "empty-state-section") {
                ul(class: "empty-state-section-list") {
                    li(class: "content-block") {
                        a(href: "${rootURL}/${app.securityRealm.loginUrl}?from=${request.requestURI}",
                                class: "content-block__link") {
                            span("Log in to Jenkins")
                            span(class: "trailing-icon") {
                                l.svgIcon(
                                        class: "icon-sm",
                                        href: "${resURL}/images/material-icons/svg-sprite-navigation-symbol.svg#ic_arrow_forward_24px")
                            }
                        }
                    }

                    if (canSignUp) {
                        li(class: "content-block") {
                            a(href: "signup", class: "content-block__link") {
                                span("Sign up for Jenkins")
                                span(class: "trailing-icon") {
                                    l.svgIcon(
                                            class: "icon-sm",
                                            href: "${resURL}/images/material-icons/svg-sprite-navigation-symbol.svg#ic_arrow_forward_24px")
                                }

                            }
                        }
                    }
                }
            }
        }
    }
}
