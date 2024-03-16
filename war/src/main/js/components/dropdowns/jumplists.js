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
    dropdownChevron.className = "jenkins-menu-dropdown-chevron";
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
    "li.children, #menuSelector, .jenkins-menu-dropdown-chevron",
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

/**
 * @typedef SwagTing
 * @type {object}
 * @property {string} type - an ID.
 * @property {string} displayName - your name.
 * @property {{order: number}} group - your age.
 * @property {string} icon - your name.
 * @property {string} iconXml - your name.
 * @property {string} url - your name.
 * @property {string} post - your name.
 * @property {{text: string, tooltip: string, severity: string}} badge - your name.
 * @property {string} semantic - your name.
 * @property {boolean} requiresConfirmation - your name.
 * @property {string} message - your name.
 * */

/**
 * Generates the contents for the dropdown
 * @param {SwagTing[]}  items
 */
function mapChildrenItemsToDropdownItems(items) {
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

        const response = []

        if (initialGroup != null && item.group.order !== initialGroup && item.group.order > 3) {
response.push({
  type: "SEPARATOR",
})
        }
        initialGroup = item.group.order;

        response.push({
          icon: item.icon,
          iconXml: item.iconXml,
          label: item.displayName,
          url: item.url,
          type: item.post || item.requiresConfirmation ? "button" : "link",
          badge: item.badge,
          clazz: item.semantic ? 'jenkins-!-' + item.semantic?.toLowerCase() + '-color' : '',
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
                });
                notificationBar.show(
                  item.displayName + ": Done.",
                  notificationBar.SUCCESS,
                );
              }
            }
          },
          subMenu: item.subMenu
            ? () => {
                return mapChildrenItemsToDropdownItems(item.subMenu.items);
              }
            : null,
        });
        return response;
  })
}

export default { init };
