import tippy from "tippy.js"

tippy("[tooltip]", {
  content: element => element.getAttribute("tooltip"),
  arrow: false,
  theme: "tooltip",
  animation: "tooltip"
})

tippy("[html-tooltip]", {
  content: element => element.getAttribute("html-tooltip"),
  allowHTML: true,
  arrow: false,
  theme: "tooltip",
  animation: "tooltip"
})

function hoverNotification(text, element) {
  let tooltip = tippy(element, {
    interactive: true,
    trigger: "hover",
    followCursor: "initial",
    arrow: false,
    theme: "tooltip",
    offset: [0, 0],
    animation: "tooltip",
    content: text,
    onShow(instance) {
      setTimeout(() => {
        instance.hide()
      }, 3000)
    },
  })
  tooltip.show()
}

window.hoverNotification = hoverNotification
