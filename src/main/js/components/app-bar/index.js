import behaviorShim from "@/util/behavior-shim";
import Utils from "@/components/dropdowns/utils";
import Templates from "@/components/dropdowns/templates";

function init() {
  behaviorShim.specify("#auto-overflow", "-dropdowns-", 1000, (element) => {
    const template = JSON.parse(element.nextSibling.content.textContent);
    const appBarItems = Utils.mapChildrenItemsToDropdownItems(
      template.items.filter((e) => e.group.order <= 2),
    );
    const overflowItems = Utils.mapChildrenItemsToDropdownItems(
      template.items.filter((e) => e.group.order > 2),
    );

    // Append top level items to the app bar
    appBarItems.forEach((item, index) => {
      // Only the first button in an app bar should have an icon
      if (index > 0) {
        item.icon = null;
        item.iconXml = null;
      }
      element.parentNode.insertBefore(
        Templates.menuItem(item, "jenkins-button"),
        element,
      );
    });

    // Add any additional items as an overflow menu
    if (overflowItems.length > 0) {
      Utils.generateDropdown(element, (instance) => {
        instance.setContent(Utils.generateDropdownItems(overflowItems));
      });
    }
  });
}

export default { init };
