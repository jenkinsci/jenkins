import { createElementFromHtml } from "@/util/dom";
import { xmlEscape } from "@/util/security";

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

/**
 * Generates the contents for the dropdown
 * @param {DropdownItem}  menuItem
 */
function menuItem(menuItem) {
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
  let clazz = itemOptions.clazz + (itemOptions.semantic ? ' jenkins-!-' + itemOptions.semantic.toLowerCase() + '-color' : '');

  // TODO - make this better
  const tag = itemOptions.action && itemOptions.action.url ? "a" : "button";
  const url = tag === 'a' ? xmlEscape(itemOptions.action.url) : '';

  const item = createElementFromHtml(`
      <${tag} class="jenkins-dropdown__item ${clazz ? clazz : ""}" ${url ? `href="${url}"` : ""} ${itemOptions.id ? `id="${xmlEscape(itemOptions.id)}"` : ""}>
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
                        ? `<span class="jenkins-dropdown__item__badge jenkins-badge alert-${badgeSeverity}" tooltip="${badgeTooltip}">${badgeText}</span>`
                        : ``
                    }
          ${
            itemOptions.action && itemOptions.action.actions
              ? `<span class="jenkins-dropdown__item__chevron"></span>`
              : ``
          }
      </${tag}>
    `);

  if (menuItem.action && menuItem.action.postTo) {
    item.addEventListener("click", () => {
      dialog
        .confirm(menuItem.action.title, {
          message: menuItem.action.description,
          type: menuItem.semantic.toLowerCase() ?? "default",
        })
        .then(
          () => {
            const form = document.createElement("form");
            form.setAttribute("method", "POST");
            form.setAttribute("action", menuItem.action.postTo);
            crumb.appendToForm(form);
            document.body.appendChild(form);
            form.submit();
          },
          () => {},
        );
    });
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
