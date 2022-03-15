import tippy from "tippy.js"

tippy("[tooltip]", {
  content: element => element.getAttribute("tooltip"),
  arrow: false,
  theme: "tooltip",
  animation: "tooltip",
  appendTo: document.body,
})

tippy("[html-tooltip]", {
  content: element => element.getAttribute("html-tooltip"),
  allowHTML: true,
  arrow: false,
  theme: "tooltip",
  animation: "tooltip",
  appendTo: document.body,
})

/**
 * Displays a tooltip for three seconds on the provided element after interaction
 * @param {string} text - The tooltip text
 * @param {HTMLElement} element - The element to show the tooltip
 */
function hoverNotification(text, element) {
  let tooltip = tippy(element, {
    interactive: true,
    trigger: "hover",
    arrow: false,
    theme: "tooltip",
    offset: [0, 0],
    animation: "tooltip",
    content: text,
    appendTo: document.body,
    onShow(instance) {
      setTimeout(() => {
        instance.hide()
      }, 3000)
    },
  })
  tooltip.show()
}

window.hoverNotification = hoverNotification
