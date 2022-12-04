import tippy from "tippy.js";
import Templates from "@/components/dropdowns/templates";
import makeKeyboardNavigable from "@/util/keyboard";
import Path from "@/util/path";

function init() {
  generateJumplistAccessors();

  tippy(
    "li.children, #menuSelector, .jenkins-menu-dropdown-chevron",
    generateDropdown()
  );
}

/*
 * Appends a ⌄ button at the end of links which support jump lists
 */
function generateJumplistAccessors() {
  document.querySelectorAll("A.model-link").forEach((link) => {
    const isFirefox = navigator.userAgent.indexOf("Firefox") !== -1;
    // Firefox adds unwanted lines when copying buttons in text, so use a span instead
    const dropdownChevron = document.createElement(
      isFirefox ? "span" : "button"
    );
    dropdownChevron.className = "jenkins-menu-dropdown-chevron";
    dropdownChevron.setAttribute("href", link.href);
    dropdownChevron.addEventListener("click", (event) => {
      event.preventDefault();
    });
    link.appendChild(dropdownChevron);
  });
}

/*
 * Generates the Tippy dropdown for the jump lists
 * Preloads the data on hover of the jump list accessor for speed
 */
function generateDropdown(isSubmenu = false) {
  return {
    ...Templates.dropdown(),
    trigger: isSubmenu ? "mouseenter" : "click",
    placement: isSubmenu ? "right-start" : "bottom-start",
    offset: isSubmenu ? [-8, 0] : [0, 0],
    onCreate(instance) {
      instance.reference.addEventListener("mouseenter", () => {
        if (instance.loaded) {
          return;
        }

        instance.popper.addEventListener("click", () => {
          instance.hide();
        });

        const href = instance.reference.getAttribute("href");
        const jumplistType = !instance.reference.classList.contains("children")
          ? "contextMenu"
          : "childrenContextMenu";

        fetch(Path.combinePath(href, jumplistType))
          .then((response) => response.json())
          .then((json) =>
            instance.setContent(generateDropdownItems(json.items))
          )
          .catch((error) => {
            instance.setContent(`Request failed. ${error}`);
          })
          .finally(() => (instance.loaded = true));
      });
    },
  };
}

/*
 * Generates the contents for the dropdown
 */
function generateDropdownItems(items) {
  const menuItems = document.createElement("div");

  menuItems.classList.add("jenkins-dropdown");
  menuItems.append(
    ...items.map((item) => {
      if (item.type === "HEADER") {
        return Templates.heading(item.text || item.displayName);
      }

      if (item.type === "SEPARATOR") {
        return Templates.separator();
      }

      const menuItemOptions = {
        url: item.url,
        label: item.text || item.displayName,
        icon: item.icon,
        iconXml: item.iconXml,
        subMenu: item.subMenu,
        type: item.post ? "button" : "link",
      };
      const menuItem = Templates.item(menuItemOptions);

      if (item.post || item.requiresConfirmation) {
        menuItem.addEventListener("click", () => {
          if (item.requiresConfirmation) {
            if (confirm((item.text || item.displayName) + ": are you sure?")) {
              // TODO I18N
              const form = document.createElement("form");
              form.setAttribute("method", item.post ? "POST" : "GET");
              form.setAttribute("action", item.url);
              if (item.post) {
                crumb.appendToForm(form);
              }
              document.body.appendChild(form);
              form.submit();
            }
          } else {
            new Ajax.Request(item.url);
          }
        });
      }

      if (item.subMenu != null) {
        menuItem.items = item.subMenu.items;
        tippy(menuItem, generateDropdown(true));
      }

      return menuItem;
    })
  );

  makeKeyboardNavigable(
    menuItems,
    () => menuItems.querySelectorAll(".jenkins-dropdown__item"),
    Templates.SELECTED_ITEM_CLASS
  );

  return menuItems;
}

export default { init };
