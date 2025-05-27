import Utils from "@/components/dropdowns/utils";
import { createElementFromHtml } from "@/util/dom";

const OVERFLOW_ID = "jenkins-header-actions-overflow";

export default function computeActions() {
  document
    .querySelectorAll(
      ".jenkins-header__actions .jenkins-button[data-type='header-action'].jenkins-hidden",
    )
    .forEach((e) => {
      e.classList.remove("jenkins-hidden");
    });

  if (!actionsOverflows()) {
    removeOverflowButton();
    return;
  }

  const items = [];
  const actions = Array.from(
    document.querySelectorAll(
      ".jenkins-header__actions .jenkins-button[data-type='header-action']",
    ),
  ).slice(1, -1);

  const overflowButton = generateOverflowButton();

  while (actionsOverflows()) {
    const item = actions.pop();

    if (!item) {
      break;
    }

    items.unshift(item);
    item.classList.add("jenkins-hidden");
  }

  Utils.generateDropdown(
    overflowButton,
    (instance) => {
      const mappedItems = items.map((e) => {
        let icon = e.querySelector("img");
        if (icon) {
          icon = icon.src;
        }
        let iconXml = e.querySelector("svg");
        if (iconXml) {
          icon = true;
          iconXml = iconXml.outerHTML;
        }

        const span = e.querySelector("[data-type='action-label']");
        let label = e.textContent;
        if (span !== null) {
          label = span.textContent;
        }

        return {
          type: "link",
          icon: icon,
          iconXml: iconXml,
          label: label,
          url: e.href,
        };
      });

      instance.setContent(Utils.generateDropdownItems(mappedItems));
    },
    true,
    {
      trigger: "mouseenter focus",
      offset: [0, 10],
      animation: "tooltip",
    },
  );

  // We want to disable the User action href on touch devices so that they can still activate the overflow menu
  const link = document.querySelector("#root-action-UserAction");

  if (link) {
    const originalHref = link.getAttribute("href");
    const isTouchDevice = window.matchMedia("(hover: none)").matches;

    // HTMLUnit doesn't register itself as supporting hover, thus the href is removed when it shouldn't be
    if (isTouchDevice && !window.isRunAsTest) {
      link.removeAttribute("href");
    } else {
      link.setAttribute("href", originalHref);
    }
  }
}

function actionsOverflows() {
  const actions = document.querySelector(".jenkins-header__actions");
  return actions.offsetWidth > Math.max(window.innerWidth / 4.5, 150);
}

function generateOverflowButton() {
  // If an overflow menu already exists let's use that
  const overflowMenu = document.querySelector("#" + OVERFLOW_ID);
  if (overflowMenu) {
    return overflowMenu;
  }

  // Generate an overflow menu to store actions
  const element =
    createElementFromHtml(`<button id="${OVERFLOW_ID}" class="jenkins-button jenkins-button--tertiary"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-miterlimit="10" stroke-width="32" d="M80 160h352M80 256h352M80 352h352"/>
</svg>
</button>`);

  const actionsContainer = document.querySelector(".jenkins-header__actions");

  // Insert the new element before the last child
  actionsContainer.insertBefore(
    element,
    actionsContainer.children[actionsContainer.children.length - 2],
  );

  return element;
}

function removeOverflowButton() {
  const overflowButton = document.querySelector("#" + OVERFLOW_ID);

  if (overflowButton) {
    overflowButton.remove();
  }
}
