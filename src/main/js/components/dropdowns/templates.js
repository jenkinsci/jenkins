import { createElementFromHtml } from "@/util/dom";
import { xmlEscape } from "@/util/security";
import behaviorShim from "@/util/behavior-shim";
import Utils from "./utils";

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

function optionalVal(key, val) {
  if (!val) {
    return "";
  }

  return `${key}="${val}"`;
}

function optionalVals(keyVals) {
  return Object.keys(keyVals)
    .map((key) => optionalVal(key, keyVals[key]))
    .join(" ");
}

function icon(opt) {
  if (!opt.icon) {
    return "";
  }

  return `<div class="jenkins-dropdown__item__icon">${
    opt.iconXml
      ? opt.iconXml
      : `<img alt="Icon" aria-hidden="true" src="${opt.icon}" />`
  }</div>`;
}

function badge(opt) {
  if (!opt.badge) {
    return "";
  }

  let badgeText = xmlEscape(opt.badge.text);
  let badgeTooltip = xmlEscape(opt.badge.tooltip);
  let badgeSeverity = xmlEscape(opt.badge.severity);

  return `<span class="jenkins-dropdown__item__badge jenkins-badge jenkins-!-${badgeSeverity}-color" tooltip="${badgeTooltip}">${badgeText}</span>`;
}

/**
 * Generates the contents for the dropdown
 * @param {DropdownItem}  dropdownItem
 * @param {'jenkins-dropdown__item' | 'jenkins-button'}  type
 * @param {string}  context
 * @return {Element}
 */
function menuItem(dropdownItem, type = "jenkins-dropdown__item", context = "") {
  /**
   * @type {DropdownItem}
   */
  const itemOptions = Object.assign(
    {
      type: "link",
    },
    dropdownItem,
  );

  const label = xmlEscape(itemOptions.displayName);

  let clazz =
    type +
    " " +
    itemOptions.clazz +
    (itemOptions.semantic
      ? " jenkins-!-" + itemOptions.semantic.toLowerCase() + "-color"
      : "");

  // If submenu
  if (itemOptions.event && itemOptions.event.event) {
    const wrapper = createElementFromHtml(
      `<div class="jenkins-split-button"></div>`,
    );
    wrapper.appendChild(
      menuItem(
        Object.assign({}, dropdownItem, { event: dropdownItem.event.event }),
        "jenkins-button",
        context,
      ),
    );

    const button = createElementFromHtml(
      `<button class="${clazz}"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="48" d="M112 184l144 144 144-144"/></svg></button>`,
    );
    Utils.generateDropdown(
      button,
      (instance) => {
        instance.setContent(
          Utils.generateDropdownItems(dropdownItem.subMenu.items),
        );
        instance.loaded = true;
      },
      false,
      {
        appendTo: "parent",
      },
    );
    wrapper.appendChild(button);

    return wrapper;
  }

  const tag =
    itemOptions.event && itemOptions.event.type === "GET" ? "a" : "button";
  const url = tag === "a" ? context + xmlEscape(itemOptions.event.url) : "";

  const item = createElementFromHtml(`
      <${tag}
        ${optionalVals({
          class: clazz,
          href: url,
          id: itemOptions.id ? xmlEscape(itemOptions.id) : null,
          "data-html-tooltip": itemOptions.tooltip
            ? xmlEscape(itemOptions.tooltip)
            : null,
        })}>
          ${icon(itemOptions)}
          ${label}
          ${badge(itemOptions)}
          ${
            itemOptions.event &&
            itemOptions.event.actions &&
            type === "jenkins-dropdown__item"
              ? `<span class="jenkins-dropdown__item__chevron"></span>`
              : ``
          }
      </${tag}>
    `);

  // Handle special cases
  tryOnClickEvent(item, dropdownItem);
  tryLoadScripts(item, dropdownItem);
  tryPost(item, dropdownItem, context);
  tryConfirmationPost(item, dropdownItem, context);

  return item;
}

/**
 * If the menu item has a custom onClick event, add it to the element
 */
function tryOnClickEvent(element, opt) {
  if (!opt.onClick) {
    return;
  }

  element.addEventListener("click", opt.onClick);
}

/**
 * If scripts have been provided with the menu item, load them
 */
function tryLoadScripts(element, opt) {
  if (!opt.event || !opt.event.attributes) {
    return;
  }

  for (const key in opt.event.attributes) {
    element.dataset[kebabToCamelCase(key)] =
      opt.event.attributes[key].toString();
  }

  loadScriptIfNotLoaded(opt.event.javascriptUrl, element);
}

/**
 * If the menu item requires a POST, add a confirmation dialog and submit the form
 */
function tryConfirmationPost(element, opt, context) {
  if (!opt.event || !opt.event.postTo) {
    return;
  }

  element.addEventListener("click", () => {
    dialog
      .confirm(opt.event.title, {
        message: opt.event.description,
        type: opt.semantic.toLowerCase() ?? "default",
      })
      .then(
        () => {
          const form = document.createElement("form");
          form.setAttribute("method", "POST");
          form.setAttribute("action", context + xmlEscape(opt.event.postTo));
          crumb.appendToForm(form);
          document.body.appendChild(form);
          form.submit();
        },
        () => {},
      );
  });
}

/**
 * If the menu item requires a POST, do a POST rather than a GET
 */
function tryPost(element, opt, context) {
  if (!opt.event || !opt.event.url || opt.event.type !== "POST") {
    return;
  }

  element.addEventListener("click", () => {
    const form = document.createElement("form");
    form.setAttribute("method", "POST");
    form.setAttribute("action", context + xmlEscape(opt.event.url));
    crumb.appendToForm(form);
    document.body.appendChild(form);
    form.submit();
  });
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
