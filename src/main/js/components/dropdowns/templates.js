import { createElementFromHtml } from "@/util/dom";
import { xmlEscape } from "@/util/security";

const hideOnPopperBlur = {
  name: "hideOnPopperBlur",
  defaultValue: true,
  fn(instance) {
    return {
      onCreate() {
        instance.popper.addEventListener("focusout", (event) => {
          if (
            instance.props.hideOnPopperBlur &&
            event.relatedTarget &&
            !instance.popper.contains(event.relatedTarget)
          ) {
            instance.hide();
          }
        });
      },
    };
  },
};

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
    plugins: [hideOnPopperBlur],
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

function menuItem(options) {
  const itemOptions = Object.assign(
    {
      type: "link",
    },
    options,
  );

  const label = xmlEscape(itemOptions.label);
  let badgeText;
  let badgeTooltip;
  let badgeSeverity;
  if (itemOptions.badge) {
    badgeText = xmlEscape(itemOptions.badge.text);
    badgeTooltip = xmlEscape(itemOptions.badge.tooltip);
    badgeSeverity = xmlEscape(itemOptions.badge.severity);
  }
  const tag = itemOptions.type === "link" ? "a" : "button";

  const item = createElementFromHtml(`
      <${tag} class="jenkins-dropdown__item ${itemOptions.clazz ? xmlEscape(itemOptions.clazz) : ""}" ${itemOptions.url ? `href="${xmlEscape(itemOptions.url)}"` : ""} ${itemOptions.id ? `id="${xmlEscape(itemOptions.id)}"` : ""}>
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
            itemOptions.subMenu != null
              ? `<span class="jenkins-dropdown__item__chevron"></span>`
              : ``
          }
      </${tag}>
    `);

  if (options.onClick) {
    item.addEventListener("click", (event) => options.onClick(event));
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
