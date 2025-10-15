import { createElementFromHtml } from "@/util/dom";
import { xmlEscape } from "@/util/security";
import behaviorShim from "@/util/behavior-shim";

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
    duration: 250,
    onShow: (instance) => {
      // Make sure only one instance is visible at all times in case of breadcrumb
      if (
        instance.reference.classList.contains("hoverable-model-link") ||
        instance.reference.classList.contains("hoverable-children-model-link")
      ) {
        const dropdowns = document.querySelectorAll("[data-tippy-root]");
        Array.from(dropdowns).forEach((element) => {
          // Check if the Tippy.js instance exists
          if (element && element._tippy) {
            // To just hide the dropdown
            element._tippy.hide();
          }
        });
      }

      const referenceParent = instance.reference.parentNode;

      if (referenceParent.classList.contains("model-link")) {
        referenceParent.classList.add("model-link--open");
      }
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
 * @param {'jenkins-dropdown__item' | 'jenkins-button'}  type
 * @param {string}  context
 * @return {Element} TODO
 */
function menuItem(menuItem, type = "jenkins-dropdown__item", context = "") {
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
  const tag =
    itemOptions.event && itemOptions.event.type === "GET" ? "a" : "button";
  const url = tag === "a" ? context + xmlEscape(itemOptions.event.url) : "";

  function optionalVal(key, val) {
    if (val) {
      return `${key}="${val}"`
    }

    return "";
  }

  function optionalVals(keyVals) {
    return Object.keys(keyVals).map(key => optionalVal(key, keyVals[key])).join(' ');
  }

  const item = createElementFromHtml(`
      <${tag}
        ${optionalVals({
          "class": type + " " + clazz,
          "href": url,
          "id": xmlEscape(itemOptions.id),
    "data-html-tooltip": xmlEscape(itemOptions.tooltip)
        })}>
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

  // Load script if needed
  if (menuItem.event && menuItem.event.attributes) {
    for (const key in menuItem.event.attributes) {
      item.dataset[kebabToCamelCase(key)] =
        menuItem.event.attributes[key].toString();
    }

    loadScriptIfNotLoaded(menuItem.event.javascriptUrl, item);
  }

  // If generic onClick event
  if (menuItem.onClick) {
    item.addEventListener("click", menuItem.onClick);
  }

  // If its a link
  if (menuItem.event && menuItem.event.url && menuItem.event.type === "POST") {
    item.addEventListener("click", () => {
      const form = document.createElement("form");
      form.setAttribute("method", "POST");
      form.setAttribute("action", context + xmlEscape(itemOptions.event.url));
      crumb.appendToForm(form);
      document.body.appendChild(form);
      form.submit();
    });
  }

  // If its a confirmation dialog
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
            form.setAttribute(
              "action",
              context + xmlEscape(itemOptions.event.postTo),
            );
            crumb.appendToForm(form);
            document.body.appendChild(form);
            form.submit();
          },
          () => {},
        );
    });
  }
  // if (options.onKeyPress) {
  //   item.onkeypress = options.onKeyPress;
  // }
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
