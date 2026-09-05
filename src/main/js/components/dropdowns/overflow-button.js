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
      Utils.generateDropdown(
        element,
        (instance) => {
          const elements =
            element.nextElementSibling.content.children[0].children;
          const mappedItems = Utils.convertHtmlToItems(elements);

          instance.setContent(Utils.generateDropdownItems(mappedItems));
          instance.loaded = true;
        },
        false,
        {
          appendTo: "parent",
        },
      );
    },
  );
}

export default { init };
