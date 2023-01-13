import CommandPalette from "@/components/command-palette";
import Notifications from "@/components/notifications";
import SearchBar from "@/components/search-bar";
import Tooltips from "@/components/tooltips";

if (!window.isRunAsTest) {
  CommandPalette.init();
}
Notifications.init();
SearchBar.init();
Tooltips.init();
