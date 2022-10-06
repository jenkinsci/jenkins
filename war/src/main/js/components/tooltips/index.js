import tippy from "tippy.js";
import behaviorShim from "@/util/behavior-shim";

const TOOLTIP_BASE = {
  arrow: false,
  theme: "tooltip",
  animation: "tooltip",
  appendTo: document.body,
};

let tooltipInstances = [];
const globalPlugin = {
  fn(instance) {
    return {
      onCreate() {
        tooltipInstances = tooltipInstances.concat(instance);
      },
      onDestroy() {
        tooltipInstances = tooltipInstances.filter((i) => i !== instance);
      },
    };
  },
};

tippy.setDefaultProps({
  plugins: [globalPlugin],
});

/**
 * Registers tooltips for the page
 * If called again, destroys existing tooltips and registers them again (useful for progressive rendering)
 * @param {HTMLElement} container - Registers the tooltips for the given container
 */
function registerTooltips(container) {
  if (!container) {
    container = document;
  }

  tooltipInstances.forEach((instance) => {
    if (instance.props.container === container) {
      instance.destroy();
    }
  });

  tippy(
    container.querySelectorAll(
      '[tooltip]:not([tooltip=""]):not([data-html-tooltip])'
    ),
    Object.assign(
      {
        content: (element) =>
          element.getAttribute("tooltip").replace(/<br[ /]?\/?>|\\n/g, "\n"),
        container: container,
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
      TOOLTIP_BASE
    )
  );

  tippy(
    container.querySelectorAll("[data-html-tooltip]"),
    Object.assign(
      {
        content: (element) => element.getAttribute("data-html-tooltip"),
        allowHTML: true,
        container: container,
        onCreate(instance) {
          instance.props.interactive =
            instance.reference.getAttribute("data-tooltip-interactive") ===
            "true";
        },
      },
      TOOLTIP_BASE
    )
  );
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
      TOOLTIP_BASE
    )
  );
  tooltip.show();
}

function init() {
  behaviorShim.specify(
    "[tooltip], [data-html-tooltip]",
    "-tooltip-",
    1000,
    function () {
      registerTooltips(null);
    }
  );

  window.hoverNotification = hoverNotification;
}

export default { init };
