import { createElementFromHtml } from "@/util/dom";
import { xmlEscape } from "@/util/security";
import behaviorShim from "@/util/behavior-shim";

function dropdown() {
  return {
    content: "<p class='jenkins-spinner'></p>",
    interactive: true,
    trigger: "click",
    allowHTML: true,
    placement: "bottom-start",
    arrow: false,
    theme: "dropdown",
    appendTo: document.body,
    offset: [0, 0],
    animation: "dropdown",
    onShow: (instance) => {
      const referenceParent = instance.reference.parentNode;

      if (referenceParent.classList.contains("model-link")) {
        referenceParent.classList.add("model-link--open");
      }
    },
    onHide: (instance) => {
      const referenceParent = instance.reference.parentNode;
      referenceParent.classList.remove("model-link--open");
    },
  };
}

function kebabToCamelCase(str) {
  return str.replace(/-([a-z])/g, function (match, char) {
    return char.toUpperCase();
  });
}

function loadScriptIfNotLoaded(url, item) {
  // Check if the script element with the given URL already exists
  const existingScript = document.querySelector(`script[src="${url}"]`);

  if (!existingScript) {
    const script = document.createElement("script");
    script.src = url;

    script.onload = () => {
      // TODO - This is hacky
      behaviorShim.applySubtree(item, true);
    };

    document.body.appendChild(script);
  }
}

/**
 * Generates the contents for the dropdown
 * @param {DropdownItem}  menuItem
 * @return {Element}
 */
function menuItem(menuItem, type = "jenkins-dropdown__item") {
  /**
   * @type {DropdownItem}
   */
  const itemOptions = Object.assign(
    {
      type: "link",
    },
    menuItem,
  );

  const label = xmlEscape(itemOptions.displayName);
  let badgeText;
  let badgeTooltip;
  let badgeSeverity;
  if (itemOptions.badge) {
    badgeText = xmlEscape(itemOptions.badge.text);
    badgeTooltip = xmlEscape(itemOptions.badge.tooltip);
    badgeSeverity = xmlEscape(itemOptions.badge.severity);
  }

  // TODO - improve this
  let clazz =
    itemOptions.clazz +
    (itemOptions.semantic
      ? " jenkins-!-" + itemOptions.semantic.toLowerCase() + "-color"
      : "");

  // TODO - make this better
  const tag = itemOptions.event && itemOptions.event.url ? "a" : "button";
  const url = tag === "a" ? xmlEscape(itemOptions.event.url) : "";

  const item = createElementFromHtml(`
      <${tag} class="${type} ${clazz ? clazz : ""}" ${url ? `href="${url}"` : ""} ${itemOptions.id ? `id="${xmlEscape(itemOptions.id)}"` : ""}>
          ${
            itemOptions.icon
              ? `<div class="jenkins-dropdown__item__icon">${
                  itemOptions.iconXml
                    ? itemOptions.iconXml
                    : `<img alt="${label}" src="${itemOptions.icon}" />`
                }</div>`
              : ``
          }
          ${label}
                    ${
                      itemOptions.badge != null
                        ? `<span class="jenkins-dropdown__item__badge jenkins-badge jenkins-!-${badgeSeverity}-color" tooltip="${badgeTooltip}">${badgeText}</span>`
                        : ``
                    }
          ${
            itemOptions.event && itemOptions.event.actions
              ? `<span class="jenkins-dropdown__item__chevron"></span>`
              : ``
          }
      </${tag}>
    `);

  if (menuItem.event && menuItem.event.attributes) {
    for (const key in menuItem.event.attributes) {
      item.dataset[kebabToCamelCase(key)] =
        menuItem.event.attributes[key].toString();
    }

    loadScriptIfNotLoaded(menuItem.event.javascriptUrl, item);
  }

  if (menuItem.onClick) {
    item.addEventListener('click', menuItem.onClick);
  }

  if (menuItem.event && menuItem.event.postTo) {
    item.addEventListener("click", () => {
      dialog
        .confirm(menuItem.event.title, {
          message: menuItem.event.description,
          type: menuItem.semantic.toLowerCase() ?? "default",
        })
        .then(
          () => {
            const form = document.createElement("form");
            form.setAttribute("method", "POST");
            form.setAttribute("action", menuItem.event.postTo);
            crumb.appendToForm(form);
            document.body.appendChild(form);
            form.submit();
          },
          () => {},
        );
    });
  }
  if (options.onKeyPress) {
    item.onkeypress = options.onKeyPress;
  }
  return item;
}

function heading(label) {
  return createElementFromHtml(
    `<p class="jenkins-dropdown__heading">${label}</p>`,
  );
}

function separator() {
  return createElementFromHtml(
    `<div class="jenkins-dropdown__separator"></div>`,
  );
}

function placeholder(label) {
  return createElementFromHtml(
    `<p class="jenkins-dropdown__placeholder">${label}</p>`,
  );
}

function disabled(label) {
  return createElementFromHtml(
    `<p class="jenkins-dropdown__disabled">${label}</p>`,
  );
}

export default {
  dropdown,
  menuItem,
  heading,
  separator,
  placeholder,
  disabled,
};
