import behaviorShim from "@/util/behavior-shim";
import Utils from "@/components/dropdowns/utils";
import Templates from "@/components/dropdowns/templates";

/**
 * Generates inline actions and an overflow menu if necessary.
 */
function init() {
  behaviorShim.specify(
    "[data-type='auto-overflow']",
    "-dropdowns-",
    1000,
    (element) => {
      const template = JSON.parse(element.nextSibling.content.textContent);
      const topLevelActions = Utils.mapChildrenItemsToDropdownItems(
        template.items.filter((e) => e.group.order <= 2),
      );

      // Append top-level items next to the overflow menu
      topLevelActions.forEach((item, index) => {
        // Only the first button in an app bar should have an icon
        if (index > 0) {
          item.icon = null;
          item.iconXml = null;
        }
        element.parentNode.insertBefore(
          Templates.menuItem(item, "jenkins-button", template.url),
          element,
        );
      });
    },
  );
}

export default { init };
