import Utils from "@/components/dropdowns/utils";
import { createElementFromHtml } from "@/util/dom";

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
          tooltip = href.getAttribute("tooltip");
          href = href.href;
        }

        return {
          type: "link",
          clazz: "jenkins-breadcrumbs__overflow-item",
          label: e.textContent,
          url: href,
          tooltip,
        };
      });

      instance.setContent(Utils.generateDropdownItems(mappedItems));
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
