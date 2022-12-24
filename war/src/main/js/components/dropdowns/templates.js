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
  };
}

function menuItem(options) {
  const itemOptions = Object.assign(
    {
      type: "link",
    },
    options
  );

  const label = xmlEscape(itemOptions.label);
  const tag = itemOptions.type === "link" ? "a" : "button";

  const item = createElementFromHtml(`
      <${tag} class="jenkins-dropdown__item" href="${itemOptions.url}">
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
            itemOptions.subMenu != null
              ? `<span class="jenkins-dropdown__item__chevron"></span>`
              : ``
          }
      </${tag}>
    `);

  if (options.onClick) {
    item.addEventListener("click", () => options.onClick());
  }

  return item;
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

export default {
  dropdown,
  menuItem,
  heading,
  separator,
};
