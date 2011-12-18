import hudson.Functions

def l = namespace(lib.LayoutTagLib)

l.layout {
    l.header(title:"Jenkins")
    l.main_panel {
        h1 {
            img(src:"${imagesURL}/48x48/error.png",alt:"[!]",height:48,width:48)
            text(" ")
            text(_("Error"))
        }
        p(_("msg"))
        pre(Functions.printThrowable(my.cause))
    }
}