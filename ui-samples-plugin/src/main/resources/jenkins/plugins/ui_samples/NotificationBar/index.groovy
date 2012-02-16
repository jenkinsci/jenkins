package jenkins.plugins.ui_samples.NotificationBar;

import lib.JenkinsTagLib

def st=namespace("jelly:stapler")

t=namespace(JenkinsTagLib.class)

namespace("/lib/samples").sample(title:_("Notification Bar")) {
    p(_("blurb"))

    p("To show a notification bar, call <tt>notificationBar.show('message')");
    button(onclick:"notificationBar.show('This is a notification');", "Show a notification bar")

    p(_("blurb.hide"))
    button(onclick:"notificationBar.hide();", "Hide it now")
}
