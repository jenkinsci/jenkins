package hudson.ProxyConfiguration

def f=namespace(lib.FormTagLib)
def l=namespace(lib.LayoutTagLib)

if (!h.hasPermission(app.ADMINISTER)) {
    set("displayOnlyMode", "true")
}

f.entry(title:_("Server"),field:"name") {
    f.textbox()
}
f.entry(title:_("Port"),field:"port") {
    f.number(clazz:"number",min:0,max:65535,step:1)
}
f.entry(title:_("User name"),field:"userName") {
    f.textbox()
}
f.entry(title:_("Password"),field:"secretPassword") {
    f.password()
}
f.entry(title:_("No Proxy Host"),field:"noProxyHost") {
    f.textarea()
}

l.hasPermission(permission: app.pluginManager.CONFIGURE_UPDATECENTER) {
    f.advanced() {
        f.entry(title: _("Test URL"), field: "testUrl") {
            f.textbox()
        }
        f.validateButton(title: _("Validate Proxy"),
                method: "validateProxy", with: "testUrl,name,port,userName,password,noProxyHost")
    }
}
