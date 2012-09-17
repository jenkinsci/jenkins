package lib.form

/**
 * Generates a UI for selecting server TCP/IP port (for some kind of daemon, typically)
 *
 * The user can specify a fixed port (1-65535), or let Jenkins allocate random port (0), or disable it (-1).
 *
 * On the databinding side, use {@link jenkins.util.ServerTcpPort} to handle this structure back into a single
 * port number. The getter method should just expose the port number integer.
 */


int port = instance?instance[field]:0;

def f=namespace(lib.FormTagLib)

def type = "${field}.type"
def id   = "${field}Id" // TODO: get rid of this

div(name:field) {
    label {
        f.radio(name: type, value:"fixed",
                checked:port>0, onclick:"\$('${id}').disabled=false")
        text(_("Fixed"))
        text(" : ")
    }
    input(type:"number", "class":"number", name:"value", id:id,
            value: port>0 ? port : null, disabled: port>0 ? null : "true",
            min:0, max:65535, step:1)

    raw("&nbsp;") ////////////////////////////

    label {
        f.radio(name:type, value:"random",
                checked:port==0, onclick:"\$('${id}').disabled=true")
        text(_("Random"))
    }

    raw("&nbsp;") ////////////////////////////

    label {
        f.radio(name:type, value:"disable",
                checked:port==-1, onclick:"\$('${id}').disabled=true")
        text(_("Disable"))
    }
}
