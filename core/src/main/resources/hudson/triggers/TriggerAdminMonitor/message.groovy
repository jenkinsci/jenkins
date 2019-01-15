package hudson.triggers.TriggerAdminMonitor

import hudson.triggers.TriggerAdminMonitor

TriggerAdminMonitor tam = my

style("""
        #cron-triggers-warning-table th {
            text-align: left;
        }
""")

div("class": "warning") {
    dl {
        h3  {
             text(_("Warning messages for cron triggers"))
             text(" (")
             a(href: tam.getUrl()+"/clear") {
                 text(_("clear all"))
             }
             text(")")
        }
        table(class: "pane sortable bigtable", width: "100%", id: "cron-triggers-warning-table") {
            tr {
                th("Trigger");
                th("Time")
                th("Message")
            }

            tam.errors.each { String trigger, TriggerAdminMonitor.Value val ->
                tr {
                    td(trigger)
                    td(val.time)
                    td(val.msg)
                }
            }
        }
    }
    p()
}
