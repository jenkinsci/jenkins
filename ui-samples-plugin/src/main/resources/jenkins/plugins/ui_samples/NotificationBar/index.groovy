package jenkins.plugins.ui_samples.NotificationBar;

import lib.JenkinsTagLib

def st=namespace("jelly:stapler")

t=namespace(JenkinsTagLib.class)

namespace("/lib/samples").sample(title:_("Notification Bar")) {
    raw(_("blurb"))

    raw("To show a notification bar, call <tt>notificationBar.show('message')</tt>")
    button(onclick:"notificationBar.show('This is a notification');", "Show a notification bar")

    raw(_("blurb.hide"))
    button(onclick:"notificationBar.hide();", "Hide it now")

    raw(_("blurb.stock"))
    button(onclick:"notificationBar.show('it worked!',          notificationBar.OK   );", "OK")
    button(onclick:"notificationBar.show('something went wrong',notificationBar.WARNING);", "WARNING")
    button(onclick:"notificationBar.show('something went wrong',notificationBar.ERROR);", "ERROR")
}
