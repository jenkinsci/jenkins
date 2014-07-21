package hudson.ProxyConfiguration;

def f=namespace(lib.FormTagLib)

f.entry(title:_("Server"),field:"name") {
    f.textbox()
}
f.entry(title:_("Port"),field:"port") {
    f.number(clazz:"number",min:0,max:65535,step:1)
}
f.entry(title:_("User name"),field:"userName") {
    f.textbox()
}
f.entry(title:_("Password"),field:"password") {
    f.password(value:instance?.encryptedPassword)
}
f.entry(title:_("No Proxy Host"),field:"noProxyHost") {
    f.textarea()
}
f.advanced(){
    f.entry(title:_("Test URL"),field:"testUrl") {
        f.textbox()
    }
    f.validateButton(title:_("Validate Proxy"), 
                     method:"validateProxy", with:"testUrl,name,port,userName,password,noProxyHost")
}
