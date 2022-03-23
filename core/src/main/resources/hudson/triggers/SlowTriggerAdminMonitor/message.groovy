package hudson.triggers.SlowTriggerAdminMonitor

import hudson.Util
import hudson.triggers.SlowTriggerAdminMonitor
import jenkins.model.Jenkins
import org.apache.commons.jelly.tags.fmt.FmtTagLibrary

SlowTriggerAdminMonitor tam = my

dl {
    div(class: "alert alert-warning") {
        form(method: "post", name: "clear", action: rootURL + "/" + tam.url + "/clear") {
            input(name: "clear", type: "submit", value: gettext("Dismiss"), class: "submit-button primary")
        }

        text(gettext("blurb"))

        table(class: "pane sortable bigtable", width: "100%") {
            tr {
                th(gettext("Trigger"))
                th(gettext("Most Recent Occurrence"))
                th(gettext("Most Recently Occurring Job"))
                th(gettext("Duration"))
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
                            a(job.getFullDisplayName(), href: job.getUrl(), class: 'model-link')
                        }
                    }
                    td(Util.getTimeSpanString(val.duration))
                }
            }
        }
    }
}
