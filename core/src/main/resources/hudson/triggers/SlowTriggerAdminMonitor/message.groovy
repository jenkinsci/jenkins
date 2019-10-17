package hudson.triggers.SlowTriggerAdminMonitor

import hudson.triggers.SlowTriggerAdminMonitor

SlowTriggerAdminMonitor tam = my

dl {
    div(class: "alert alert-warning") {
        form(method: "post", name: "clear", action: rootURL + "/" + tam.url + "/clear") {
            input(name: "clear", type: "submit", value: _("Dismiss"), class: "submit-button primary")
        }

        text(_("Warning messages for cron triggers"))

        style("""
            #cron-triggers-warning-table th {
                text-align: left;
            }
        """)

        table(class: "pane sortable bigtable", width: "100%", id: "cron-triggers-warning-table") {
            tr {
                th(_("Trigger"))
                th(_("Time"))
                th(_("Message"))
            }

            tam.errors.each { trigger, val ->
                tr {
                    td(trigger)
                    td(val.time)
                    td(val.msg)
                }
            }
        }
    }
}
