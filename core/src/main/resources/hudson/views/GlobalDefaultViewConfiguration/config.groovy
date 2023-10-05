package hudson.views.GlobalDefaultViewConfiguration

def f=namespace(lib.FormTagLib)

if (app.views.size()>1) {
    f.entry(title:_("Default view"), field:"defaultView") {
        div(class:"jenkins-select") {
            select(name:"primaryView", class:"jenkins-select__input") {
                app.views.each { v ->
                    f.option(value:v.viewName, selected:app.primaryView==v) {
                        text(v.viewName)
                    }
                }
            }
        }
    }
}
