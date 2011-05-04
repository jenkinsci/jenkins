import hudson.taglibs.LayoutTagLib

l=namespace(LayoutTagLib)
t=namespace("/lib/hudson")
st=namespace("jelly:stapler")

def feature(String icon, String href, String title, Closure body=null) {
    t.summary(icon:icon, href:href, iconOnly:true) {
        div(class:"link") {
            a(href:href, title)
        }
        div(style:"color:gray; text-decoration:none;",body)
    }
}

l.layout(title:i18n("Manage Jenkins"),permission:app.ADMINISTER) {

    st.include(page:"sidepanel.jelly")
    l.main_panel {
        h1(i18n("Manage Jenkins"))

        if (my.isCheckURIEncodingEnabled()) {
            script """
                var url='checkURIEncoding';
                var params='value=\u57f7\u4e8b';
                var checkAjax=new Ajax.Updater(
                  'message', url,
                  {
                    method: 'get', parameters: params
                  }
                );
            """
            span(id:"message")
        }

        app.administrativeMonitors.each { am ->
            if (am.isActivated() && am.isEnabled())
                st.include(it:am, page:"message.jelly")
        }

        st.include(page:"downgrade.jelly")

        table(style:"padding-left: 2em;",id:"management-links") {
            feature("setting.gif", "configure", i18n("Configure System")) {
               raw(i18n("Configure global settings and paths."))
            }
            // TODO: more features

            app.managementLinks.each { m ->
                if (m.iconFileName==null)   return;
                feature(m.iconFileName, m.urlName, m.displayName) {
                    raw(m.description)
                }
            }

            if (app.quietingDown) {
                feature("system-log-out.gif","cancelQuietDown",i18n("Cancel Shutdown"))
            } else {
                feature("system-log-out.gif","quietDown",i18n("Prepare for Shutdown")) {
                    raw(i18n("Stops executing new builds, so that the system can be eventually shut down safely."))
                }
            }
        }
    }
}