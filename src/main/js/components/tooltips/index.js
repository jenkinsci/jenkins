import tippy from "tippy.js";
import behaviorShim from "@/util/behavior-shim";

const rootStyles = getComputedStyle(document.documentElement);
const sectionPaddingVar =
  rootStyles.getPropertyValue("--section-padding") || "1";
let sectionPaddingRem = Number.parseFloat(sectionPaddingVar);
if (Number.isNaN(sectionPaddingRem)) {
  sectionPaddingRem = 1;
}

const rootFontSize = Number.parseFloat(rootStyles.fontSize) || 16;
const sectionPaddingPx = sectionPaddingRem * rootFontSize;

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
          padding: sectionPaddingPx,
        },
      },
    ],
  },
  duration: 250,
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

  let reference = element;
  if (element.classList && element.classList.contains("jenkins-badge")) {
    let clickableAncestor = element.closest(
      ".task-link, .jenkins-section__item > a, .jenkins-section__item, a, button",
    );
    if (clickableAncestor) {
      reference = clickableAncestor;
    }
  }

  let tooltip = element.getAttribute("tooltip");
  let htmlTooltip = element.getAttribute("data-html-tooltip");
  let defaultDelay = 250;
  let delay = element.getAttribute("data-tooltip-delay") || defaultDelay;
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
      reference,
      Object.assign(
        {
          trigger: "mouseenter focus",
          delay: [delay, 0],
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
      ),
    );
  }

  if (htmlTooltip !== null && htmlTooltip.trim().length > 0) {
    tippy(
      reference,
      Object.assign(
        {
          trigger: "mouseenter focus",
          delay: [delay, 0],
          content: () => htmlTooltip,
          allowHTML: true,
          onCreate(instance) {
            instance.props.interactive =
              instance.reference.getAttribute("data-tooltip-interactive") ===
              "true";
          },
          appendTo: appendTo,
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
