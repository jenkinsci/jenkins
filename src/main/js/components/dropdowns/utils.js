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
function generateDropdown(element, callback, immediate, options = {}) {
  if (element._tippy && element._tippy.props.theme === "dropdown") {
    element._tippy.destroy();
  }

  tippy(
    element,
    Object.assign(
      {},
      Templates.dropdown(),
      {
        onCreate(instance) {
          const onload = () => {
            if (instance.loaded) {
              return;
            }

            document.addEventListener("click", (event) => {
              const isClickOnReference = instance.reference.contains(
                event.target,
              );
              // Don't close the dropdown if the user is interacting with a SELECT menu inside of it
              const isSelect = event.target.tagName === "SELECT";

              if (!isClickOnReference && !isSelect) {
                instance.clickToHide = true;
                instance.hide();
              }
            });

            callback(instance);
          };
          if (immediate) {
            onload();
          } else {
            ["mouseenter", "focus"].forEach((event) => {
              instance.reference.addEventListener(event, onload);
            });
          }
        },
        onHide(instance) {
          const referenceParent = instance.reference.parentNode;
          referenceParent.classList.remove("model-link--open");
          if (
            instance.props.trigger === "mouseenter" &&
            !instance.clickToHide
          ) {
            const dropdowns = document.querySelectorAll("[data-tippy-root]");
            const isMouseOverAnyDropdown = Array.from(dropdowns).some(
              (dropdown) => dropdown.matches(":hover"),
            );

            return !isMouseOverAnyDropdown;
          }

          instance.clickToHide = false;
          return true;
        },
      },
      options,
    ),
  );
}

/**
 * Generates the contents for the dropdown
 * @param {DropdownItem[]}  items
 * @param {boolean}  compact
 * @param {string}  context
 */
function generateDropdownItems(items, compact = false, context = "") {
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
        return Templates.heading(item.displayName);
      }

      if (item.type === "SEPARATOR") {
        return Templates.separator();
      }

      if (item.type === "DISABLED") {
        return Templates.disabled(item.displayName);
      }

      const menuItem = Templates.menuItem(
        item,
        "jenkins-dropdown__item",
        context,
      );

      if (item.event && item.event.actions != null) {
        tippy(
          menuItem,
          Object.assign({}, Templates.dropdown(), {
            content: generateDropdownItems(item.event.actions),
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

function validateDropdown(e) {
  if (e.targetUrl) {
    const method = e.getAttribute("checkMethod") || "post";
    try {
      FormChecker.delayedCheck(e.targetUrl(), method, e.targetElement);
    } catch (x) {
      console.warn(x);
    }
  }
}

function getMaxSuggestionCount(e, defaultValue) {
  return parseInt(e.dataset["maxsuggestions"]) || defaultValue;
}

function debounce(callback) {
  callback.running = false;
  return () => {
    if (!callback.running) {
      callback.running = true;
      setTimeout(() => {
        callback();
        callback.running = false;
      }, 300);
    }
  };
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

/**
 * @param {HTMLElement[]} children
 * @return {DropdownItem[]}
 */
function convertHtmlToItems(children) {
  return [...children].map((child) => {
    const attributes = child.dataset;

    /** @type {DropdownItemType} */
    const type = child.dataset.dropdownType;

    switch (type) {
      case "ITEM": {
        /** @type {MenuItemDropdownItem} */
        const item = {
          type: "ITEM",
          displayName: attributes.dropdownText,
          id: attributes.dropdownId,
          icon: attributes.dropdownIcon,
          iconXml: attributes.dropdownIcon,
          clazz: attributes.dropdownClazz,
          semantic: attributes.dropdownSemantic,
        };

        if (attributes.dropdownConfirmationTitle) {
          item.event = {
            title: attributes.dropdownConfirmationTitle,
            description: attributes.dropdownConfirmationDescription,
            postTo: attributes.dropdownConfirmationUrl,
          };
        }

        if (attributes.dropdownHref) {
          item.event = {
            url: attributes.dropdownHref,
            type: "GET",
          };
        }

        return item;
      }
      case "SUBMENU":
        /** @type {MenuItemDropdownItem} */
        return {
          type: "ITEM",
          displayName: attributes.dropdownText,
          icon: attributes.dropdownIcon,
          iconXml: attributes.dropdownIcon,
          event: {
            actions: convertHtmlToItems(child.content.children),
          },
        };
      case "SEPARATOR":
        return { type: type };
      case "HEADER":
        return { type: type, displayName: attributes.dropdownText };
      case "CUSTOM":
        return { type: type, contents: child.content.cloneNode(true) };
    }
  });
}

export default {
  generateDropdown,
  generateDropdownItems,
  validateDropdown,
  getMaxSuggestionCount,
  debounce,
  mapChildrenItemsToDropdownItems,
  convertHtmlToItems
};
