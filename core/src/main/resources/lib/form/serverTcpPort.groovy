package lib.form

/**
 * Generates a UI for selecting server TCP/IP port (for some kind of daemon, typically)
 *
 * The user can specify a fixed port (1-65535), or let Jenkins allocate random port (0), or disable it (-1).
 *
 * On the databinding side, use {@link jenkins.util.ServerTcpPort} to handle this structure back into a single
 * port number. The getter method should just expose the port number integer.
 */

int port = instance ? instance[field] : 0

def f = namespace(lib.FormTagLib)

def type = "${field}.type"

div(name: field) {
    f.radio(name: type, value: "fixed", title: _("Fixed"), id: "radio-${field}-fixed", checked: port > 0) {
        input(type: "number", class: "jenkins-input", name: "value", id: "${field}Id", placeholder: _("Port"),
                value: port > 0 ? port : null, min: 0, max: 65535, step: 1)
    }

    f.radio(name: type, value: "random", title: _("Random"), id: "radio-${field}-random", checked: port == 0)

    f.radio(name: type, value: "disable", title: _("Disable"), id: "radio-${field}-disable", checked: port == -1)
}
