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
    ".hoverable-model-link, .hoverable-children-model-link",
    "-hoverable-dropdown-",
    1000,
    (element) =>
      Utils.generateDropdown(
        element,
        async (instance) => {
          const href = element.href;

          if (element.items) {
            instance.setContent(Utils.generateDropdownItems(element.items));
            return;
          }

          const hasModelLink = element.classList.contains(
            "hoverable-model-link",
          );
          const hasChildrenLink = element.classList.contains(
            "hoverable-children-model-link",
          );

          const sections = {
            model: null,
            children: null,
          };

          const fetchSection = async (urlSuffix) => {
            const response = await fetch(Path.combinePath(href, urlSuffix));
            const json = await response.json();
            const items = mapChildrenItemsToDropdownItems(json.items);
            const section = document.createElement("div");
            section.appendChild(Utils.generateDropdownItems(items));

            return section;
          };

          try {
            const promises = [];

            if (hasModelLink) {
              promises.push(
                fetchSection("contextMenu").then(
                  (section) => (sections.model = section),
                ),
              );
            }

            if (hasChildrenLink) {
              promises.push(
                fetchSection("childrenContextMenu").then(
                  (section) => (sections.children = section),
                ),
              );
            }

            await Promise.all(promises);

            const container = document.createElement("div");
            container.className = "jenkins-dropdown__split-container";
            if (sections.model) {
              container.appendChild(sections.model);
            }
            if (sections.children) {
              container.appendChild(sections.children);
            }

            instance.setContent(container);
          } catch (error) {
            console.log(`Dropdown fetch failed: ${error}`);
          } finally {
            instance.loaded = true;
          }
        },
        false,
        {
          trigger: "mouseenter",
          offset: [-16, 10],
          animation: "tooltip",
          touch: false,
        },
      ),
  );
}

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
