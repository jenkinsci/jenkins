import hotkeys from "hotkeys-js"

window.addEventListener("load", () => {
  const searchBar = document.querySelector("#search-box")
  searchBar.placeholder = searchBar.placeholder + ` (${translateKeyboardShortcutForOS("CMD+K")
    .replace("CMD", "⌘")})`

  hotkeys(translateKeyboardShortcutForOS("CMD+K"), () => {
    searchBar.focus()

    // Returning false stops the event and prevents default browser events
    return false
  })
})

/**
 * Translates a given keyboard shortcut, e.g. CMD+K, into an OS neutral version, e.g. CTRL+K
 * @param {string} keyboardShortcut The shortcut for translation
 */
function translateKeyboardShortcutForOS(keyboardShortcut) {
  const useCmdKey = navigator.platform.toUpperCase().indexOf("MAC") >= 0 ||
    navigator.platform.toUpperCase() === "IPHONE"
  return keyboardShortcut.replace("CMD", useCmdKey ? "CMD" : "CTRL")
}
