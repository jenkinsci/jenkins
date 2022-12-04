import tippy from "tippy.js";
import Templates from "@/components/dropdowns/templates";
import makeKeyboardNavigable from "@/util/keyboard";
import { toId } from "@/util/dom";

/*
 * Generates a jump list for the active breadcrumb to jump to
 * sections on the page (if using <f:breadcrumb-config-outline />)
 */
function init() {
  const inpageNavigationBreadcrumb = document.querySelector("#inpage-nav");
  if (inpageNavigationBreadcrumb) {
    const chevron = document.createElement("li");
    chevron.classList.add("children");
    inpageNavigationBreadcrumb.after(chevron);

    tippy(chevron, generateDropdown());
  }
}

/*
 * Generates the Tippy dropdown for the in-page jump list
 */
function generateDropdown() {
  return {
    ...Templates.dropdown(),
    onShow(instance) {
      instance.popper.addEventListener("click", () => {
        instance.hide();
      });
      const items = [];
      document
        .querySelectorAll(
          "form > div > div > .jenkins-section > .jenkins-section__title"
        )
        .forEach((section) => {
          const id = toId(section.textContent);
          section.id = id;
          items.push({ label: section.textContent, id: id });
        });
      instance.setContent(generateDropdownItems(items));
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
      const menuItemOptions = {
        url: "#" + item.id,
        label: item.label,
      };
      return Templates.item(menuItemOptions);
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
