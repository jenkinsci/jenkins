import Utils from "@/components/dropdowns/utils";
import { createElementFromHtml } from "@/util/dom";
import Path from "@/util/path";

/**
 * Maps context menu items from the server response to dropdown item format.
 * This follows the same pattern used in jumplists.js for consistency.
 */
function mapContextMenuItems(items) {
  return items.map((item) => {
    if (item.type === "HEADER") {
      return { type: "HEADER", label: item.displayName };
    }
    if (item.type === "SEPARATOR") {
      return { type: "SEPARATOR" };
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
            // dialog, crumb, notificationBar are globals from Jenkins
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
      subMenu: item.subMenu ? () => mapContextMenuItems(item.subMenu.items) : null,
    };
  });
}

/**
 * Creates a dropdown content callback for a collapsed breadcrumb item.
 * This fetches the context menu and children context menu on demand.
 */
function createContextMenuCallback(hasModel, hasChildren, href) {
  return (instance) => {
    const sections = {
      model: null,
      children: null,
    };

    const fetchSection = (urlSuffix) => {
      return fetch(Path.combinePath(href, urlSuffix))
        .then((response) => response.json())
        .then((json) => Utils.generateDropdownItems(mapContextMenuItems(json.items)));
    };

    const promises = [];

    if (hasModel === "true") {
      promises.push(
        fetchSection("contextMenu").then((section) => {
          section.prepend(
            createElementFromHtml(
              `<p class="jenkins-dropdown__heading">Actions</p>`,
            ),
          );
          sections.model = section;
        }),
      );
    }

    if (hasChildren === "true") {
      promises.push(
        fetchSection("childrenContextMenu").then((section) => {
          section.prepend(
            createElementFromHtml(
              `<p class="jenkins-dropdown__heading">Navigation</p>`,
            ),
          );
          sections.children = section;
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
          // Merge both sections into one dropdown for proper a11y
          const dropbox = sections.model;
          Array.from(sections.children.children).forEach((item) => {
            dropbox.appendChild(item);
          });
          container.appendChild(dropbox);
        }

        instance.setContent(container);
      })
      .catch((error) => {
        console.log(`Breadcrumb context menu fetch failed: ${error}`);
      })
      .finally(() => {
        instance.loaded = true;
      });
  };
}

export default function computeBreadcrumbs() {
  document
    .querySelectorAll(".jenkins-breadcrumbs__list-item.jenkins-hidden")
    .forEach((e) => {
      e.classList.remove("jenkins-hidden");
    });

  if (!breadcrumbsBarOverflows()) {
    removeOverflowButton();
    return;
  }

  const items = [];
  const breadcrumbs = Array.from(
    document.querySelectorAll(`[data-type="breadcrumb-item"]`),
  );

  const breadcrumbsOverflow = generateOverflowButton().querySelector("button");

  while (breadcrumbsBarOverflows()) {
    const item = breadcrumbs.shift();

    if (!item) {
      break;
    }

    items.push(item);
    item.classList.add("jenkins-hidden");
  }

  Utils.generateDropdown(
    breadcrumbsOverflow,
    (instance) => {
      const mappedItems = items.map((e) => {
        let href = e.querySelector("a");
        let tooltip;
        if (href) {
          href = href.href;
        }
        if (e.textContent.length > 26) {
          tooltip = e.textContent;
        }

        return {
          type: "link",
          clazz: "jenkins-breadcrumbs__overflow-item",
          label: e.textContent,
          url: href,
          tooltip,
        };
      });

      const content = Utils.generateDropdownItems(mappedItems);

      // Attach context menu dropdowns to overflow items that had them originally
      items.forEach((breadcrumbItem, index) => {
        const dropdownIndicator =
          breadcrumbItem.querySelector(".dropdown-indicator");
        if (!dropdownIndicator) {
          return;
        }

        const hasModel = dropdownIndicator.getAttribute("data-model");
        const hasChildren = dropdownIndicator.getAttribute("data-children");
        const dataHref = dropdownIndicator.getAttribute("data-href");

        if ((hasModel === "true" || hasChildren === "true") && dataHref) {
          const overflowMenuItem = content.children[index];
          if (overflowMenuItem) {
            // Attach nested dropdown using the same pattern as jumplists.js
            Utils.generateDropdown(
              overflowMenuItem,
              createContextMenuCallback(hasModel, hasChildren, dataHref),
              false,
              {
                trigger: "mouseenter",
                placement: "right-start",
                offset: [-8, 0],
                animation: "tooltip",
                touch: false,
              },
            );
          }
        }
      });

      instance.setContent(content);
    },
    true,
    {
      trigger: "click focus",
      offset: [0, 10],
      animation: "tooltip",
    },
  );
}

function breadcrumbsBarOverflows() {
  const breadcrumbsBar = document.querySelector("#breadcrumbBar");
  return breadcrumbsBar.scrollWidth > breadcrumbsBar.offsetWidth;
}

function generateOverflowButton() {
  // If an overflow menu already exists let's use that
  const overflowMenu = document.querySelector(
    ".jenkins-breadcrumbs__list-item .jenkins-button",
  );
  if (overflowMenu) {
    return overflowMenu.parentNode;
  }

  // Generate an overflow menu to store breadcrumbs
  const logo = document.querySelector(".jenkins-breadcrumbs__list-item");
  const element =
    createElementFromHtml(`<li class="jenkins-breadcrumbs__list-item"><button class="jenkins-button jenkins-button--tertiary"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
    <circle cx="256" cy="256" r="45" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"/>
    <circle cx="441" cy="256" r="45" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"/>
    <circle cx="71" cy="256" r="45" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"/>
</svg>
</button></li>`);
  logo.after(element);
  return element;
}

function removeOverflowButton() {
  const breadcrumbsOverflow = document.querySelector(
    ".jenkins-breadcrumbs__list-item .jenkins-button",
  );

  if (breadcrumbsOverflow) {
    breadcrumbsOverflow.parentNode.remove();
  }
}
