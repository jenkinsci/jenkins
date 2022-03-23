package hudson.ProxyConfiguration

def f=namespace(lib.FormTagLib)
def l=namespace(lib.LayoutTagLib)

set("readOnlyMode", !app.hasPermission(app.ADMINISTER))

f.entry(title:gettext("Server"),field:"name") {
    f.textbox()
}
f.entry(title:gettext("Port"),field:"port") {
    f.number(clazz:"number",min:0,max:65535,step:1)
}
f.entry(title:gettext("User name"),field:"userName") {
    f.textbox()
}
f.entry(title:gettext("Password"),field:"secretPassword") {
    f.password()
}
f.entry(title:gettext("No Proxy Host"),field:"noProxyHost") {
    f.textarea()
}

l.isAdmin() {
    f.advanced() {
        f.entry(title: gettext("Test URL"), field: "testUrl") {
            f.textbox()
        }
        f.validateButton(title:gettext("Validate Proxy"),
                         method:"validateProxy", with:"testUrl,name,port,userName,secretPassword,noProxyHost")
    }
}
