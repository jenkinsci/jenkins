package hudson.PluginWrapper

def l = namespace(lib.LayoutTagLib)
def f = namespace(lib.FormTagLib)

l.layout {
    def title = "Uninstalling ${my.shortName} plugin"
    l.header(title:title)
    l.main_panel {
        h1 {
            img(src:"${imagesURL}/48x48/error.png",alt:"[!]",height:48,width:48)
            text(" ")
            text(title)
        }
        p { raw _("msg",my.shortName) }
        f.form(method:"post",action:"doUninstall") {
            f.submit(value:_("Yes"))
        }
    }
}