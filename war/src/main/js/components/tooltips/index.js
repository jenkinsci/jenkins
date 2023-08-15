import tippy from "tippy.js";
import behaviorShim from "@/util/behavior-shim";

const TOOLTIP_BASE = {
  arrow: false,
  theme: "tooltip",
  animation: "tooltip",
  appendTo: document.body,
};

/**
 * Registers tooltips for the given element
 * If called again, destroys any existing tooltip for the element and
 * registers them again (useful for progressive rendering)
 * @param {HTMLElement} element - Registers the tooltips for the given element
 */
function registerTooltip(element) {
  if (element._tippy && element._tippy.props.theme === "tooltip") {
    element._tippy.destroy();
  }

  if (
    element.hasAttribute("tooltip") &&
    !element.hasAttribute("data-html-tooltip")
  ) {
    tippy(
      element,
      Object.assign(
        {
          content: (element) =>
            element.getAttribute("tooltip").replace(/<br[ /]?\/?>|\\n/g, "\n"),
          onCreate(instance) {
            instance.reference.setAttribute("title", instance.props.content);
          },
          onShow(instance) {
            instance.reference.removeAttribute("title");
          },
          onHidden(instance) {
            instance.reference.setAttribute("title", instance.props.content);
          },
        },
        TOOLTIP_BASE,
      ),
    );
  }

  if (element.hasAttribute("data-html-tooltip")) {
    tippy(
      element,
      Object.assign(
        {
          content: (element) => element.getAttribute("data-html-tooltip"),
          allowHTML: true,
          onCreate(instance) {
            instance.props.interactive =
              instance.reference.getAttribute("data-tooltip-interactive") ===
              "true";
          },
        },
        TOOLTIP_BASE,
      ),
    );
  }
}

/**
 * Displays a tooltip for three seconds on the provided element after interaction
 * @param {string} text - The tooltip text
 * @param {HTMLElement} element - The element to show the tooltip
 */
function hoverNotification(text, element) {
  const tooltip = tippy(
    element,
    Object.assign(
      {
        trigger: "hover",
        offset: [0, 0],
        content: text,
        onShow(instance) {
          setTimeout(() => {
            instance.hide();
          }, 3000);
        },
      },
      TOOLTIP_BASE,
    ),
  );
  tooltip.show();
}

function init() {
  behaviorShim.specify(
    "[tooltip], [data-html-tooltip]",
    "-tooltip-",
    1000,
    (element) => {
      registerTooltip(element);
    },
  );

  window.hoverNotification = hoverNotification;
}

export default { init };
