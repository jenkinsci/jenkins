import { createElementFromHtml } from "@/util/dom";

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
  };
}

const SELECTED_ITEM_CLASS = "jenkins-dropdown__item--selected";

const itemDefaultOptions = {
  type: "link",
};

function item(options) {
  const itemOptions = {
    ...itemDefaultOptions,
    ...options,
  };

  const tag = itemOptions.type === "link" ? "a" : "button";

  return createElementFromHtml(`
      <${tag} class="jenkins-dropdown__item" href="${itemOptions.url}">
          ${
            itemOptions.icon
              ? `<div class="jenkins-dropdown__item__icon">${
                  itemOptions.iconXml
                    ? itemOptions.iconXml
                    : `<img src="${itemOptions.icon}" />`
                }</div>`
              : ``
          }
          ${itemOptions.label}
          ${
            itemOptions.subMenu != null
              ? `<span class="jenkins-dropdown__item__chevron"></span>`
              : ``
          }
      </${tag}>
    `);
}

function heading(label) {
  return createElementFromHtml(
    `<p class="jenkins-dropdown__heading">${label}</p>`
  );
}

function separator() {
  return createElementFromHtml(
    `<div class="jenkins-dropdown__separator"></div>`
  );
}

export default { dropdown, SELECTED_ITEM_CLASS, item, heading, separator };
