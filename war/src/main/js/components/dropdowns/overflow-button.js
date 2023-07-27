import Utils from "@/components/dropdowns/utils";
import behaviorShim from "@/util/behavior-shim";

/**
 * Creates a new dropdown based on the element's next sibling
 */
function init() {
  behaviorShim.specify(
    "[data-dropdown='true']",
    "-dropdown-",
    1000,
    (element) => {
      Utils.generateDropdown(element, (instance) => {
        instance.setContent(element.nextElementSibling.content);
      });
    },
  );
}

export default { init };
