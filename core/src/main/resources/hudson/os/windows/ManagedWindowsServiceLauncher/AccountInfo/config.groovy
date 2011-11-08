package hudson.os.windows.ManagedWindowsServiceLauncher.AccountInfo;


def f=namespace(lib.FormTagLib)

f.entry (title:_("User name"),field:"userName") {
    f.textbox()
}

f.entry (title:_("Password"),field:"password") {
    f.password()
}
