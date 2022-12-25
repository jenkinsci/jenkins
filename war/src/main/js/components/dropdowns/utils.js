import Templates from "@/components/dropdowns/templates";
import makeKeyboardNavigable from "@/util/keyboard";
import tippy from "tippy.js";

const SELECTED_ITEM_CLASS = "jenkins-dropdown__item--selected";

/*
 * Generates the dropdowns for the given element
 * Preloads the data on hover for speed
 * @param element - the element to generate the dropdown for
 * @param callback - called to retrieve the list of dropdown items
 */
function generateDropdown(element, callback) {
  tippy(
    element,
    Object.assign({}, Templates.dropdown(), {
      onCreate(instance) {
        instance.reference.addEventListener("mouseenter", () => {
          if (instance.loaded) {
            return;
          }

          instance.popper.addEventListener("click", () => {
            instance.hide();
          });

          callback(instance);
        });
      },
    })
  );
}

/*
 * Generates the contents for the dropdown
 */
function generateDropdownItems(items) {
  const menuItems = document.createElement("div");
  menuItems.classList.add("jenkins-dropdown");

  items
    .map((item) => {
      if (item.type === "HEADER") {
        return Templates.heading(item.label);
      }

      if (item.type === "SEPARATOR") {
        return Templates.separator();
      }

      const menuItem = Templates.menuItem(item);

      if (item.subMenu != null) {
        tippy(
          menuItem,
          Object.assign({}, Templates.dropdown(), {
            content: generateDropdownItems(item.subMenu()),
            trigger: "mouseenter",
            placement: "right-start",
            offset: [-8, 0],
          })
        );
      }

      return menuItem;
    })
    .forEach((item) => menuItems.appendChild(item));

  makeKeyboardNavigable(
    menuItems,
    () => menuItems.querySelectorAll(".jenkins-dropdown__item"),
    SELECTED_ITEM_CLASS,
    (selectedItem, key) => {
      switch (key) {
        case "ArrowLeft":
          selectedItem.closest("[data-tippy-root]")?._tippy?.hide();
          break;
        case "ArrowRight": {
          const tippy = selectedItem._tippy;
          if (!tippy) {
            break;
          }

          tippy.show();
          tippy.props.content
            .querySelector(".jenkins-dropdown__item")
            .classList.add(SELECTED_ITEM_CLASS);
          break;
        }
      }
    },
    (container) => {
      const isVisible =
        window.getComputedStyle(container).visibility === "visible";
      const isLastDropdown = Array.from(
        document.querySelectorAll(".jenkins-dropdown")
      )
        .filter((dropdown) => container !== dropdown)
        .filter(
          (dropdown) =>
            window.getComputedStyle(dropdown).visibility === "visible"
        )
        .every(
          (dropdown) =>
            !(
              container.compareDocumentPosition(dropdown) &
              Node.DOCUMENT_POSITION_FOLLOWING
            )
        );

      return isVisible && isLastDropdown;
    }
  );

  return menuItems;
}

export default {
  generateDropdown,
  generateDropdownItems,
};
