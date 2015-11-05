import hudson.Functions

def l = namespace(lib.LayoutTagLib)

l.layout {
    l.header(title:"Jenkins")
    l.main_panel {
        h1 {
            l.icon(class: 'icon-error icon-xlg')
            text(" ")
            text(_("Error"))
        }
        p(_("msg"))
        pre(Functions.printThrowable(my.cause))
    }
}