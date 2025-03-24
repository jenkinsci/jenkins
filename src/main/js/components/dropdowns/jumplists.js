import Path from "@/util/path";
import behaviorShim from "@/util/behavior-shim";
import Utils from "@/components/dropdowns/utils";

function init() {
  generateJumplistAccessors();
  generateDropdowns();
}

/*
 * Appends a ⌄ button at the end of links which support jump lists
 */
function generateJumplistAccessors() {
  behaviorShim.specify("A.model-link", "-jumplist-", 999, (link) => {
    const isFirefox = navigator.userAgent.indexOf("Firefox") !== -1;
    // Firefox adds unwanted lines when copying buttons in text, so use a span instead
    const dropdownChevron = document.createElement(
      isFirefox ? "span" : "button",
    );
    dropdownChevron.setAttribute("aria-label", "Open dropdown menu");
    dropdownChevron.dataset.href = link.href;
    dropdownChevron.addEventListener("click", (event) => {
      event.preventDefault();
    });
    link.appendChild(dropdownChevron);
  });
}

/*
 * Generates the dropdowns for the jump lists
 */
function generateDropdowns() {
  behaviorShim.specify(
    "li.children, .jenkins-jumplist-link, #menuSelector, .jenkins-menu-dropdown-chevron",
    "-dropdown-",
    1000,
    (element) =>
      Utils.generateDropdown(element, (instance) => {
        const href = element.dataset.href;
        const jumplistType = !element.classList.contains("children")
          ? "contextMenu"
          : "childrenContextMenu";

        if (element.items) {
          instance.setContent(Utils.generateDropdownItems(element.items));
          return;
        }

        fetch(Path.combinePath(href, jumplistType))
          .then((response) => response.json())
          .then((json) =>
            instance.setContent(
              Utils.generateDropdownItems(
                mapChildrenItemsToDropdownItems(json.items),
              ),
            ),
          )
          .catch((error) => console.log(`Jumplist request failed: ${error}`))
          .finally(() => (instance.loaded = true));
      }),
  );
}

/*
 * Generates the contents for the dropdown
 */
function mapChildrenItemsToDropdownItems(items) {
  return items.map((item) => {
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

    return {
      icon: item.icon,
      iconXml: item.iconXml,
      label: item.displayName,
      url: item.url,
      type: item.post || item.requiresConfirmation ? "button" : "link",
      badge: item.badge,
      onClick: () => {
        if (item.post || item.requiresConfirmation) {
          if (item.requiresConfirmation) {
            dialog
              .confirm(item.displayName, { message: item.message })
              .then(() => {
                const form = document.createElement("form");
                form.setAttribute("method", item.post ? "POST" : "GET");
                form.setAttribute("action", item.url);
                if (item.post) {
                  crumb.appendToForm(form);
                }
                document.body.appendChild(form);
                form.submit();
              });
          } else {
            fetch(item.url, {
              method: "post",
              headers: crumb.wrap({}),
            }).then((rsp) => {
              if (rsp.ok) {
                notificationBar.show(
                  item.displayName + ": Done.",
                  notificationBar.SUCCESS,
                );
              } else {
                notificationBar.show(
                  item.displayName + ": Failed.",
                  notificationBar.ERROR,
                );
              }
            });
          }
        }
      },
      subMenu: item.subMenu
        ? () => {
            return mapChildrenItemsToDropdownItems(item.subMenu.items);
          }
        : null,
    };
  });
}

export default { init };
