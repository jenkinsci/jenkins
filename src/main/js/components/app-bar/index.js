import behaviorShim from "@/util/behavior-shim";
import Utils from "@/components/dropdowns/utils";
import Templates from "@/components/dropdowns/templates";

function init() {
  behaviorShim.specify("#auto-overflow", "-dropdosswn-", 1000, (element) => {
    const template = JSON.parse(element.nextSibling.content.textContent);
    const appBarItems = mapChildrenItemsToDropdownItems(
      template.items.filter((e) => e.group.order <= 2),
    );
    const overflowItems = mapChildrenItemsToDropdownItems(
      template.items.filter((e) => e.group.order > 2),
    );

    // Append top level items to the app bar
    appBarItems.forEach((item, index) => {
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

/**
 * Generates the contents for the dropdown
 * @param {DropdownItem[]}  items
 * @return {DropdownItem[]}
 */
function mapChildrenItemsToDropdownItems(items) {
  /** @type {number | null} */
  let initialGroup = null;

  return items.flatMap((item) => {
    if (item.type === "HEADER") {
      return {
        type: "HEADER",
        label: item.displayName,
      };
    }

    if (item.type === "SEPARATOR") {
      return {
        type: "SEPARATOR",
      };
    }

    const response = [];

    if (
      initialGroup != null &&
      item.group?.order !== initialGroup &&
      item.group.order > 2
    ) {
      response.push({
        type: "SEPARATOR",
      });
    }
    initialGroup = item.group?.order;

    response.push(item);
    return response;
  });
}

export default { init };
