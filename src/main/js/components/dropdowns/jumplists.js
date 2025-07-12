import Utils from "@/components/dropdowns/utils";
import behaviorShim from "@/util/behavior-shim";
import { createElementFromHtml } from "@/util/dom";
import Path from "@/util/path";

function init() {
  generateJumplistAccessors();
  generateDropdowns();
}
function generateDropdownChevron(element) {
  const isFirefox = navigator.userAgent.indexOf("Firefox") !== -1;
  // Firefox adds unwanted lines when copying buttons in text, so use a span instead
  const dropdownChevron = document.createElement(isFirefox ? "span" : "button");
  dropdownChevron.className = "jenkins-menu-dropdown-chevron";
  dropdownChevron.dataset.href = element.href;
  dropdownChevron.addEventListener("click", (event) => {
    event.preventDefault();
  });
  element.appendChild(dropdownChevron);
}

/*
 * Appends a ⌄ button at the end of links which support jump lists
 */
function generateJumplistAccessors() {
  behaviorShim.specify("A.model-link", "-jumplist-", 999, (link) => {
    generateDropdownChevron(link);
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
        createDropdownContent(
          element,
          element.classList.contains("hoverable-model-link"),
          element.classList.contains("hoverable-children-model-link"),
          element.href,
        ),
        false,
        {
          trigger: "mouseenter",
          offset: [-16, 10],
          animation: "tooltip",
          touch: false,
        },
      ),
  );

  behaviorShim.specify(
    ".dropdown-indicator",
    "-clickable-dropdown-",
    1000,
    (element) =>
      Utils.generateDropdown(
        element,
        createDropdownContent(
          element,
          element.getAttribute("data-model"),
          element.getAttribute("data-children"),
          element.getAttribute("data-href"),
        ),
        false,
        {
          trigger: "click focus",
          offset: [-16, 10],
          animation: "tooltip",
          touch: false,
        },
      ),
  );

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

function createDropdownContent(element, hasModelLink, hasChildrenLink, href) {
  return (instance) => {
    if (element.items) {
      instance.setContent(Utils.generateDropdownItems(element.items));
      return;
    }
    const sections = {
      model: null,
      children: null,
    };

    const fetchSection = function (urlSuffix) {
      return fetch(Path.combinePath(href, urlSuffix))
        .then((response) => response.json())
        .then((json) => {
          const items = Utils.generateDropdownItems(
            mapChildrenItemsToDropdownItems(json.items),
          );
          return items;
        });
    };

    const promises = [];

    if (hasModelLink === "true") {
      promises.push(
        fetchSection("contextMenu").then((section) => {
          const dContainer = section;
          dContainer.prepend(
            createElementFromHtml(
              `<p class="jenkins-dropdown__heading">Actions</p>`,
            ),
          );
          sections.model = dContainer;
        }),
      );
    }

    if (hasChildrenLink === "true") {
      promises.push(
        fetchSection("childrenContextMenu").then((section) => {
          const dContainer = section;
          // add a header for the section
          dContainer.prepend(
            createElementFromHtml(
              `<p class="jenkins-dropdown__heading">Navigation</p>`,
            ),
          );
          sections.children = dContainer;
        }),
      );
    }

    Promise.all(promises)
      .then(() => {
        const container = document.createElement("div");
        container.className = "jenkins-dropdown__split-container";
        if (sections.model && !sections.children) {
          container.appendChild(sections.model);
        } else if (!sections.model && sections.children) {
          container.appendChild(sections.children);
        } else if (sections.model && sections.children) {
          // use the first dropdown and add the second dropdowns choices this way the a11y stays intact
          const dropbox = sections.model;
          Array.from(sections.children.children).forEach((item) => {
            dropbox.appendChild(item);
          });
          container.appendChild(dropbox);
        }
        instance.setContent(container);
      })
      .catch((error) => {
        console.log(`Dropdown fetch failed: ${error}`);
      })
      .finally(() => {
        instance.loaded = true;
      });
  };
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
