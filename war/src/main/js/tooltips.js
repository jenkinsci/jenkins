import tippy from "tippy.js"

const TOOLTIP_BASE = {
  arrow: false,
  theme: "tooltip",
  animation: "tooltip",
  appendTo: document.body
}

registerTooltips()

/**
 * Registers tooltips for the page
 * If called again, destroys existing tooltips and registers them again (useful for progressive rendering)
 */
function registerTooltips() {
  [...document.querySelectorAll("*")].forEach(node => {
    if (node._tippy) {
      node._tippy.destroy()
    }
  })

  tippy("[tooltip]", {
    content: element => element.getAttribute("tooltip"),
    ...TOOLTIP_BASE
  })

  tippy("[html-tooltip]", {
    content: element => element.getAttribute("html-tooltip"),
    allowHTML: true,
    ...TOOLTIP_BASE
  })
}

/**
 * Displays a tooltip for three seconds on the provided element after interaction
 * @param {string} text - The tooltip text
 * @param {HTMLElement} element - The element to show the tooltip
 */
function hoverNotification(text, element) {
  const tooltip = tippy(element, {
    interactive: true,
    trigger: "hover",
    offset: [0, 0],
    content: text,
    ...TOOLTIP_BASE,
    onShow(instance) {
      setTimeout(() => {
        instance.hide()
      }, 3000)
    },
  })
  tooltip.show()
}

window.registerTooltips = registerTooltips
window.hoverNotification = hoverNotification
