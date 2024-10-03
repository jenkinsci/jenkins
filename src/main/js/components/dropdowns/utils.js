import Templates from "@/components/dropdowns/templates";
import makeKeyboardNavigable from "@/util/keyboard";
import tippy from "tippy.js";
import behaviorShim from "@/util/behavior-shim";

const SELECTED_ITEM_CLASS = "jenkins-dropdown__item--selected";

/*
 * Generates the dropdowns for the given element
 * Preloads the data on hover for speed
 * @param element - the element to generate the dropdown for
 * @param callback - called to retrieve the list of dropdown items
 */
function generateDropdown(element, callback, immediate) {
  tippy(
    element,
    Object.assign({}, Templates.dropdown(), {
      hideOnClick: element.dataset["hideOnClick"] !== "false",
      onCreate(instance) {
        const onload = () => {
          if (instance.loaded) {
            return;
          }

          instance.popper.addEventListener("click", () => {
            instance.hide();
          });

          callback(instance);
        };
        if (immediate) {
          onload();
        } else {
          instance.reference.addEventListener("mouseenter", onload);
        }
      },
    }),
  );
}

/*
 * Generates the contents for the dropdown
 */
function generateDropdownItems(items, compact) {
  const menuItems = document.createElement("div");
  menuItems.classList.add("jenkins-dropdown");
  if (compact === true) {
    menuItems.classList.add("jenkins-dropdown--compact");
  }

  items
    .map((item) => {
      if (item.type === "CUSTOM") {
        return item.contents;
      }

      if (item.type === "HEADER") {
        return Templates.heading(item.label);
      }

      if (item.type === "SEPARATOR") {
        return Templates.separator();
      }

      if (item.type === "DISABLED") {
        return Templates.disabled(item.label);
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
          }),
        );
      }

      return menuItem;
    })
    .forEach((item) => menuItems.appendChild(item));

  if (items.length === 0) {
    menuItems.appendChild(Templates.placeholder("No items"));
  }

  makeKeyboardNavigable(
    menuItems,
    () => menuItems.querySelectorAll(".jenkins-dropdown__item"),
    SELECTED_ITEM_CLASS,
    (selectedItem, key, evt) => {
      if (!selectedItem) {
        return;
      }
      switch (key) {
        case "ArrowLeft": {
          const root = selectedItem.closest("[data-tippy-root]");
          if (root) {
            const tippyReference = root._tippy;
            if (tippyReference) {
              tippyReference.hide();
            }
          }
          break;
        }
        case "ArrowRight": {
          const tippyRef = selectedItem._tippy;
          if (!tippyRef) {
            break;
          }

          tippyRef.show();
          tippyRef.props.content
            .querySelector(".jenkins-dropdown__item")
            .classList.add(SELECTED_ITEM_CLASS);
          break;
        }
        default:
          if (selectedItem.onkeypress) {
            selectedItem.onkeypress(evt);
          }
      }
    },
    (container) => {
      const isVisible =
        window.getComputedStyle(container).visibility === "visible";
      const isLastDropdown = Array.from(
        document.querySelectorAll(".jenkins-dropdown"),
      )
        .filter((dropdown) => container !== dropdown)
        .filter(
          (dropdown) =>
            window.getComputedStyle(dropdown).visibility === "visible",
        )
        .every(
          (dropdown) =>
            !(
              container.compareDocumentPosition(dropdown) &
              Node.DOCUMENT_POSITION_FOLLOWING
            ),
        );

      return isVisible && isLastDropdown;
    },
  );

  behaviorShim.applySubtree(menuItems);

  return menuItems;
}

function convertHtmlToItems(children) {
  const items = [];
  Array.from(children).forEach((child) => {
    const attributes = child.dataset;
    const type = child.dataset.dropdownType;

    switch (type) {
      case "ITEM": {
        const item = {
          label: attributes.dropdownText,
          id: attributes.dropdownId,
          icon: attributes.dropdownIcon,
          iconXml: attributes.dropdownIcon,
          clazz: attributes.dropdownClazz,
        };

        if (attributes.dropdownHref) {
          item.url = attributes.dropdownHref;
          item.type = "link";
        } else {
          item.type = "button";
        }

        items.push(item);
        break;
      }
      case "SUBMENU":
        items.push({
          type: "ITEM",
          label: attributes.dropdownText,
          icon: attributes.dropdownIcon,
          iconXml: attributes.dropdownIcon,
          subMenu: () => convertHtmlToItems(child.content.children),
        });
        break;
      case "SEPARATOR":
        items.push({ type: type });
        break;
      case "HEADER":
        items.push({ type: type, label: attributes.dropdownText });
        break;
      case "CUSTOM":
        items.push({ type: type, contents: child.content.cloneNode(true) });
        break;
    }
  });
  return items;
}

export default {
  convertHtmlToItems,
  generateDropdown,
  generateDropdownItems,
};
