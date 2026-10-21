import behaviorShim from "@/util/behavior-shim";
import Utils from "@/components/dropdowns/utils";
import Templates from "@/components/dropdowns/templates";
import { xmlEscape } from "@/util/security";

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
        template.items,
      );
      const compact = element.dataset.compact === "true";

      // Append top-level items next to the overflow menu
      topLevelActions.forEach((item, index) => {
        if (compact) {
          // Dense contexts such as list rows have no room for labels,
          // so fall back to a tooltip and match the tertiary button styling
          item.tooltip = xmlEscape(item.displayName);
          item.displayName = "";
          item.clazz = [item.clazz, "jenkins-button--tertiary"]
            .filter(Boolean)
            .join(" ");
        } else if (index > 0) {
          // Only the first button in an app bar should have an icon
          item.icon = null;
          item.iconXml = null;
        }
        const button = Templates.menuItem(item, "jenkins-button", template.url);
        element.parentNode.insertBefore(button, element);
        behaviorShim.applySubtree(button, true);
      });
    },
  );
}

export default { init };
