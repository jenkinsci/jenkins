import AppBar from "@/components/app-bar";
import Dropdowns from "@/components/dropdowns";
import CommandPalette from "@/components/command-palette";
import Notifications from "@/components/notifications";
import SearchBar from "@/components/search-bar";
import Tooltips from "@/components/tooltips";
import StopButtonLink from "@/components/stop-button-link";
import ConfirmationLink from "@/components/confirmation-link";
import Dialogs from "@/components/dialogs";
import Defer from "@/components/defer";

function showPageLoadNotification() {
  const { notificationMessage, notificationType } = document.body.dataset;
  if (!notificationMessage) {
    return;
  }

  const options =
    (notificationType && window.notificationBar[notificationType]) || undefined;
  window.notificationBar.show(notificationMessage, options);
  delete document.body.dataset.notificationMessage;
  delete document.body.dataset.notificationType;
}

AppBar.init();
Dropdowns.init();
CommandPalette.init();
Defer.init();
Notifications.init();
showPageLoadNotification();
SearchBar.init();
Tooltips.init();
StopButtonLink.init();
ConfirmationLink.init();
Dialogs.init();
