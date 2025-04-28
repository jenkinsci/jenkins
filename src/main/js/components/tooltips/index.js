import tippy, { followCursor } from "tippy.js";
import behaviorShim from "@/util/behavior-shim";

const TOOLTIP_BASE = {
  arrow: false,
  theme: "tooltip",
  animation: "tooltip",
  touch: false,
  popperOptions: {
    modifiers: [
      {
        name: "preventOverflow",
        options: {
          boundary: "viewport",
          padding:
            parseFloat(
              getComputedStyle(document.documentElement).getPropertyValue(
                "--section-padding",
              ),
            ) * 16,
        },
      },
    ],
  },
  duration: 250,
  maxWidth: "min(50vw, 1000px)",
  plugins: [followCursor],
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

  const tooltip = element.getAttribute("tooltip");
  const htmlTooltip = element.getAttribute("data-html-tooltip");
  const dataTooltipOptions = element.getAttribute("data-tooltip-options");
  let tooltipOptions = {};
  if (dataTooltipOptions !== null) {
    const options = JSON.parse(dataTooltipOptions);
    [
      "placement",
      "delay",
      "maxWidth",
      "followCursor",
      "interactive",
      "offset",
    ].forEach((opt) => {
      if (opt in options) {
        tooltipOptions[opt] = options[opt];
      }
    });
  }
  const delay = element.getAttribute("data-tooltip-delay");
  if (delay) {
    tooltipOptions.delay = delay;
  }
  const interactive = element.getAttribute("data-tooltip-interactive");
  if (interactive) {
    tooltipOptions.interactive = interactive === "true";
  }
  let appendTo = document.body;
  if (element.hasAttribute("data-tooltip-append-to-parent")) {
    appendTo = "parent";
  }
  if (
    tooltip !== null &&
    tooltip.trim().length > 0 &&
    (htmlTooltip === null || htmlTooltip.trim().length == 0)
  ) {
    tippy(
      element,
      Object.assign(
        {
          content: () => tooltip.replace(/<br[ /]?\/?>|\\n/g, "\n"),
          onCreate(instance) {
            instance.reference.setAttribute("title", instance.props.content);
          },
          onShow(instance) {
            instance.reference.removeAttribute("title");
          },
          onHidden(instance) {
            instance.reference.setAttribute("title", instance.props.content);
          },
          appendTo: appendTo,
        },
        TOOLTIP_BASE,
        tooltipOptions,
      ),
    );
  }

  if (htmlTooltip !== null && htmlTooltip.trim().length > 0) {
    tippy(
      element,
      Object.assign(
        {
          content: () => htmlTooltip,
          allowHTML: true,
          appendTo: appendTo,
        },
        TOOLTIP_BASE,
        tooltipOptions,
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
