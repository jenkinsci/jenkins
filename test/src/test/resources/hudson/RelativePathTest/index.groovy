package hudson.RelativePathTest

def l = namespace(lib.LayoutTagLib)
def j = namespace("jelly:core")
def f= namespace(lib.FormTagLib)

l.layout {
    l.main_panel {
        set("instance",my)
        set("descriptor",my.descriptor)
        f.form() {
            f.entry(field:"name") {
                f.textbox()
            }
            f.property(field:"model")
        }
    }
}
