import tippy from "tippy.js"

const TOOLTIP_BASE = {
  arrow: false,
  theme: "tooltip",
  animation: "tooltip",
  appendTo: document.body
}

let tooltipInstances = []
const globalPlugin = {
  fn() {
    return {
      onCreate(instance) {
        tooltipInstances = tooltipInstances.concat(instance)
      },
      onDestroy(instance) {
        tooltipInstances = tooltipInstances.filter(i => i !== instance)
      }
    }
  }
}

tippy.setDefaultProps({
  plugins: [globalPlugin]
})

registerTooltips()

/**
 * Registers tooltips for the page
 * If called again, destroys existing tooltips and registers them again (useful for progressive rendering)
 * @param {HTMLElement} container - Registers the tooltips for the given container
 */
function registerTooltips(container) {
  if (!container) {
    container = document
  }

  tooltipInstances.forEach(instance => {
    if (instance.props.container === container) {
      instance.destroy()
    }
  })

  tippy(container.querySelectorAll("[tooltip]:not([tooltip=\"\"])"), {
    content: element => element.getAttribute("tooltip")
      .replace("<br>", "\n")
      .replace("<br/>", "\n")
      .replace("<br />", "\n")
      .replace("\\n", "\n"),
    container: container,
    ...TOOLTIP_BASE,
    onCreate(instance) {
      instance.reference.setAttribute("title", instance.props.content)
    },
    onShow(instance) {
      instance.reference.removeAttribute("title")
    },
    onHidden(instance) {
      instance.reference.setAttribute("title", instance.props.content)
    }
  })

  tippy(container.querySelectorAll("[html-tooltip]"), {
    content: element => element.getAttribute("html-tooltip"),
    allowHTML: true,
    container: container,
    interactive: true,
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
