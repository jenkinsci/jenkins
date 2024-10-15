package hudson.triggers.SlowTriggerAdminMonitor

import hudson.Util
import hudson.triggers.SlowTriggerAdminMonitor
import jenkins.model.Jenkins
import org.apache.commons.jelly.tags.fmt.FmtTagLibrary

SlowTriggerAdminMonitor tam = my

dl {
    div(class: "jenkins-alert jenkins-alert-warning") {
        form(method: "post", name: "clear", action: rootURL + "/" + tam.url + "/clear") {
            button(name: "clear", type: "submit", class: "jenkins-button jenkins-submit-button jenkins-button--primary") {
                raw _("Dismiss")
            }
        }

        text(_("blurb"))

        table(class: "sortable jenkins-table jenkins-!-margin-top-2", width: "100%") {
            thead {
                tr {
                    th(_("Trigger"))
                    th(_("Most Recent Occurrence"))
                    th(_("Most Recently Occurring Job"))
                    th(_("Duration"))
                }
            }

            tam.errors.each { trigger, val ->
                def job = Jenkins.get().getItemByFullName(val.fullJobName)

                tr {
                    td(Jenkins.get().getDescriptorByType(val.trigger).getDisplayName())
                    td(val.getTimeString())
                    if (job == null) {
                        td(val.fullJobName)
                    } else {
                        td {
                            a(job.getFullDisplayName(), href: rootURL + '/' + job.getUrl(), class: 'model-link')
                        }
                    }
                    td(Util.getTimeSpanString(val.duration))
                }
            }
        }
    }
}
